package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.tourism.application.policy.RecommendationPolicy
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.RecommendedSpotsUseCase
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.port.out.query.TasteSimilarityQueryPort
import org.around.ieuri.tourism.application.query.RecommendedSpotsQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.validation.coordinatesOf
import org.around.ieuri.tourism.application.validation.requireRadiusWithin
import org.around.ieuri.tourism.domain.strategy.RankedSpot
import org.around.ieuri.tourism.domain.strategy.TasteNormalization
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 내 주변 추천(SPEC-0035). 반경 안 후보에서 반응한 장소를 빼고 점수·카테고리 다양성으로 앞 `first`건을 준다.
 * - 반응이 최소량 미만이거나 벡터가 없으면 거리만으로 점수를 받는다.
 * - 유사도 조회 실패는 오류가 아니라 취향 없음이다.
 */
@Service
@Transactional(readOnly = true)
class RecommendedSpotsQueryService(
    private val spotQueryPort: SpotQueryPort,
    // 반경 조회는 PostGIS 생 SQL 포트다.
    private val spotSpatialQueryPort: SpotSpatialQueryPort,
    // 취향 유사도는 pgvector 생 SQL 포트다.
    private val tasteSimilarityQueryPort: TasteSimilarityQueryPort,
    private val eventPublisher: TourismEventPublisherPort,
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
        val byId = candidates.associateBy { it.spotId }
        val ranked = candidates.map {
            RankedSpot(it.spotId, it.category, policy.score.score(it.distanceMeters, it.title, taste[it.spotId]))
        }
        return policy.diversity.order(ranked).take(first).map { byId.getValue(it).toView() }
    }

    /** 후보별 취향 요인. 반응이 최소량 미만이거나 좋아요 평균 벡터가 없으면 비어 있다. */
    private fun tasteOf(
        liked: Set<Long>,
        disliked: Set<Long>,
        candidates: List<SpotSearchResult>,
        locale: DomainLocale,
    ): Map<Long, Double> {
        if (candidates.isEmpty() || liked.size + disliked.size < policy.tasteMinReactions) return emptyMap()
        val similarities = try {
            tasteSimilarityQueryPort.findSimilarities(
                likedCatalogIds = liked,
                dislikedCatalogIds = disliked,
                candidateCatalogIds = candidates.map { it.spotId },
                language = locale,
                model = policy.embedding.model,
            )
        } catch (e: RuntimeException) {
            log.warn("취향 유사도 조회 실패, 취향 없이 진행: {}", e.javaClass.simpleName)
            return emptyMap()
        }
        // 후보 집단 안에서 0~1로 펼친다.
        return TasteNormalization.normalize(
            similarities.mapNotNull { (id, s) ->
                s.like?.let { like -> id to policy.score.tasteFactor(like, s.dislike) }
            }.toMap(),
        )
    }

    /** attributionText는 copyright에서 파생된다. */
    private fun SpotSearchResult.toView(): SpotView = SpotView(
        id = spotId,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        distanceMeters = distanceMeters,
        category = category,
        copyright = copyright,
        attributionText = copyright.attributionText,
        mapX = mapX,
        mapY = mapY,
    )
}
