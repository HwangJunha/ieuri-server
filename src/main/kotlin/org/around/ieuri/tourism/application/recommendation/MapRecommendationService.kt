package org.around.ieuri.tourism.application.recommendation

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.catalog.provided.dto.MapSpotView
import org.around.ieuri.tourism.application.catalog.provided.exception.BboxTooLargeException
import org.around.ieuri.tourism.application.catalog.provided.exception.InvalidBboxException
import org.around.ieuri.tourism.application.catalog.required.MapQueryPort
import org.around.ieuri.tourism.application.catalog.required.SpotQueryPort
import org.around.ieuri.tourism.application.catalog.required.dto.MapCandidateSearch
import org.around.ieuri.tourism.application.catalog.required.dto.MapCandidateView
import org.around.ieuri.tourism.application.recommendation.provided.MapRecommendationUseCase
import org.around.ieuri.tourism.application.recommendation.provided.dto.MapRecommendedSpotsQuery
import org.around.ieuri.tourism.application.recommendation.required.TasteSimilarityQueryPort
import org.around.ieuri.tourism.domain.catalog.ReactionType
import org.around.ieuri.tourism.domain.recommendation.RankedSpot
import org.around.ieuri.tourism.domain.recommendation.ReactionHistory
import org.around.ieuri.tourism.domain.recommendation.ScoreReaction
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 지도 영역 추천(SPEC-0036). 영역 안 후보에서 이미 표시한 장소만 빼고 점수를 매겨
 * 카테고리 반복 감점으로 재배열한 뒤 앞 `first`건을 준다. 거리 기준점은 영역 중심점이다.
 * - 반응한 장소는 빼지 않는다 — 좋아요·싫어요는 점수 배율로만 반영된다(D-13).
 * - 같은 좌표는 점수가 가장 높은 1건으로 접는다(D-14).
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class MapRecommendationService(
    private val mapQueryPort: MapQueryPort,
    private val spotQueryPort: SpotQueryPort,
    private val tasteSimilarityQueryPort: TasteSimilarityQueryPort,
    private val policy: MapRecommendationPolicy,
    private val recommendationPolicy: RecommendationPolicy,
) : MapRecommendationUseCase {

    private val log = LoggerFactory.getLogger(MapRecommendationService::class.java)

    override fun mapRecommendedSpots(
        query: MapRecommendedSpotsQuery,
        locale: DomainLocale,
        memberId: Long,
    ): List<MapSpotView> {
        // 원시 좌표 → 도메인 VO. 범위·뒤집힘 실패는 INVALID_BBOX로 번역된다.
        val bounds = boundsOf(query.minLat, query.minLng, query.maxLat, query.maxLng)
        if (bounds.areaInSquareDegrees > policy.maxAreaInSquareDegrees) {
            throw BboxTooLargeException("지도를 확대해 주세요.")
        }
        val first = query.first.coerceIn(1, policy.maxSize)

        // 반응은 활성 언어 단위로 읽으며, 제외가 아니라 점수 배율과 취향의 재료로만 쓴다(D-13).
        val reactions = spotQueryPort.findReactedSpotIdsByReaction(memberId, locale)
        val liked = reactions[ReactionType.LIKE].orEmpty()
        val disliked = reactions[ReactionType.DISLIKE].orEmpty()

        val candidates = mapQueryPort.findRecommendationCandidatesWithin(
            MapCandidateSearch(
                language = locale,
                bounds = bounds,
                categories = query.categories,
                // 제외는 클라이언트가 이미 그린 마커뿐이다.
                excludedSpotIds = query.excludedSpotIds,
                limit = policy.candidateCap,
            ),
        )
        val taste = tasteOf(liked, disliked, candidates, locale)
        val scored = candidates.map {
            it to recommendationPolicy.score.score(
                it.distanceMeters,
                it.title,
                taste[it.id],
                reactionOf(it.id, liked, disliked),
            )
        }
        val representatives = foldByCoordinate(scored)
        val byId = representatives.associate { (candidate, _) -> candidate.id to candidate }
        val ranked = representatives.map { (candidate, score) ->
            RankedSpot(candidate.id, candidate.category, score)
        }
        return recommendationPolicy.diversity.order(ranked).take(first).map { byId.getValue(it).toView() }
    }

    /**
     * 후보별 취향 요인. 반응이 최소량 미만이면 비어 있고, 좋아요 벡터가 없는 후보는 키가 없다.
     * - 유사도 조회가 실패하면 취향 없이 진행한다.
     */
    private fun tasteOf(
        liked: Set<Long>,
        disliked: Set<Long>,
        candidates: List<MapCandidateView>,
        locale: DomainLocale,
    ): Map<Long, Double> {
        if (candidates.isEmpty() || !recommendationPolicy.tasteThreshold.isSatisfiedBy(ReactionHistory(liked, disliked))) return emptyMap()
        val similarities = try {
            tasteSimilarityQueryPort.findSimilarities(
                likedCatalogIds = liked,
                dislikedCatalogIds = disliked,
                candidateCatalogIds = candidates.map { it.id },
                language = locale,
                model = recommendationPolicy.embedding.model,
            )
        } catch (e: RuntimeException) {
            log.warn("취향 유사도 조회 실패, 취향 없이 진행: {}", e.javaClass.simpleName)
            return emptyMap()
        }
        return recommendationPolicy.score.tasteFactors(similarities)
    }

    /** 좌표가 정확히 같은 후보를 점수 최고 1건으로 접는다(D-14). 동점이면 id 오름차순이다. */
    private fun foldByCoordinate(
        scored: List<Pair<MapCandidateView, Double>>,
    ): List<Pair<MapCandidateView, Double>> = scored.groupBy { (candidate, _) -> candidate.lng to candidate.lat }
        .values
        .map { group ->
            group.sortedWith(
                compareByDescending<Pair<MapCandidateView, Double>> { it.second }
                    .thenBy { it.first.id },
            ).first()
        }

    private fun reactionOf(id: Long, liked: Set<Long>, disliked: Set<Long>): ScoreReaction = when (id) {
        in liked -> ScoreReaction.LIKED
        in disliked -> ScoreReaction.DISLIKED
        else -> ScoreReaction.NONE
    }

    private fun MapCandidateView.toView(): MapSpotView = MapSpotView(
        id = id,
        title = title,
        thumbnailImageUrl = thumbnailImageUrl,
        category = category,
        lng = lng,
        lat = lat,
    )

    /** 뷰포트 사각형을 만든다. [MapBounds]의 불변식 위반은 INVALID_BBOX로 번역한다. */
    private fun boundsOf(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): MapBounds = MapBounds.ofOrNull(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)
        ?: throw InvalidBboxException()
}
