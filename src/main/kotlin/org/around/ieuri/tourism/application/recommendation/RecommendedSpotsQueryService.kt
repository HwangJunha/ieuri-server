package org.around.ieuri.tourism.application.recommendation

import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.catalog.SpotQueryPolicy
import org.around.ieuri.tourism.application.catalog.coordinatesOf
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.application.catalog.requireRadiusWithin
import org.around.ieuri.tourism.application.catalog.required.SpotQueryPort
import org.around.ieuri.tourism.application.catalog.required.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.catalog.required.dto.NearbySpotSearch
import org.around.ieuri.tourism.application.recommendation.provided.RecommendedSpotsUseCase
import org.around.ieuri.tourism.application.recommendation.provided.dto.RecommendedSpotsQuery
import org.around.ieuri.tourism.application.recommendation.required.RecommendationEventPublisherPort
import org.around.ieuri.tourism.application.recommendation.required.TasteSimilarityQueryPort
import org.around.ieuri.tourism.domain.catalog.ReactionType
import org.around.ieuri.tourism.domain.recommendation.RankedSpot
import org.around.ieuri.tourism.domain.recommendation.ReactionHistory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 내 주변 추천(SPEC-0035). 반경 안 후보에서 반응한 장소를 빼고 점수·카테고리 다양성으로 앞 `first`건을 준다.
 * - 반응이 최소량 미만이거나 벡터가 없으면 거리만으로 점수를 받는다.
 * - 유사도 조회 실패는 오류가 아니라 취향 없음이다.
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class RecommendedSpotsQueryService(
    private val spotQueryPort: SpotQueryPort,
    private val spotSpatialQueryPort: SpotSpatialQueryPort,
    private val tasteSimilarityQueryPort: TasteSimilarityQueryPort,
    private val eventPublisher: RecommendationEventPublisherPort,
    private val spotQueryPolicy: SpotQueryPolicy,
    private val policy: RecommendationPolicy,
) : RecommendedSpotsUseCase {

    private val log = LoggerFactory.getLogger(RecommendedSpotsQueryService::class.java)

    override fun recommendedSpots(query: RecommendedSpotsQuery, locale: DomainLocale, memberId: Long): List<SpotView> {
        val center = coordinatesOf(query.latitude, query.longitude)
        requireRadiusWithin(query.radiusMeters, spotQueryPolicy.maxRadiusMeters)
        val first = query.first.coerceIn(1, spotQueryPolicy.maxPageSize)
        // 조회 사실을 남긴다(SPEC-0032 R19). 좌표는 넘기지 않는다.
        eventPublisher.publish(LocationAccessed(memberId, "recommendedSpots"))

        // 반응 제외는 DB 단계에서 활성 언어 단위로 한다.
        val reactions = spotQueryPort.findReactedSpotIdsByReaction(memberId, locale)
        val liked = reactions[ReactionType.LIKE].orEmpty()
        val disliked = reactions[ReactionType.DISLIKE].orEmpty()
        val candidates = spotSpatialQueryPort.searchNearby(
            NearbySpotSearch(
                language = locale,
                center = center,
                radiusMeters = query.radiusMeters,
                categories = query.categories,
                excludedSpotIds = liked + disliked,
                limit = policy.candidateCap,
                offset = 0,
            ),
        )
        val taste = tasteOf(liked, disliked, candidates, locale)
        val byId = candidates.associateBy { it.id }
        val ranked = candidates.map {
            RankedSpot(it.id, it.category, policy.ranking.score(it.distanceMeters, it.title, taste[it.id]))
        }
        return policy.ranking.order(ranked).take(first).map { byId.getValue(it) }
    }

    /** 후보별 취향 요인. 반응이 최소량 미만이거나 좋아요 평균 벡터가 없으면 비어 있다. */
    private fun tasteOf(
        liked: Set<Long>,
        disliked: Set<Long>,
        candidates: List<SpotView>,
        locale: DomainLocale,
    ): Map<Long, Double> {
        if (candidates.isEmpty() || !policy.tasteThreshold.isSatisfiedBy(ReactionHistory(liked, disliked))) return emptyMap()
        val similarities = try {
            tasteSimilarityQueryPort.findSimilarities(
                likedCatalogIds = liked,
                dislikedCatalogIds = disliked,
                candidateCatalogIds = candidates.map { it.id },
                language = locale,
                model = policy.embedding.model,
            )
        } catch (e: RuntimeException) {
            log.warn("취향 유사도 조회 실패, 취향 없이 진행: {}", e.javaClass.simpleName)
            return emptyMap()
        }
        return policy.ranking.tasteFactors(similarities)
    }
}
