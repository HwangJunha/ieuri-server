package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.tourism.application.exception.BboxTooLargeException
import org.around.ieuri.tourism.application.policy.MapViewportPolicy
import org.around.ieuri.tourism.application.policy.RecommendationPolicy
import org.around.ieuri.tourism.application.port.`in`.query.MapRecommendationUseCase
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.query.MapCandidateSearch
import org.around.ieuri.tourism.application.port.out.query.MapCandidateView
import org.around.ieuri.tourism.application.port.out.query.MapQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.TasteSimilarityQueryPort
import org.around.ieuri.tourism.application.query.MapRecommendedSpotsQuery
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.application.validation.boundsOf
import org.around.ieuri.tourism.domain.strategy.RankedSpot
import org.around.ieuri.tourism.domain.strategy.TasteNormalization
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 지도 영역 추천(SPEC-0036). 보고 있는 영역 안 후보에서 반응한 장소와 이미 표시한 장소를 빼고,
 * 점수(거리·취향·면세)를 매긴 뒤 카테고리 반복 감점으로 재배열해 앞 `first`건을 준다.
 *
 * 점수 계산은 SPEC-0035가 정한 것을 그대로 쓴다 — 다른 것은 **거리 기준점이 영역 중심점**이고
 * 후보를 반경이 아니라 사각형으로 조달한다는 점뿐이다. 원으로 근사하면 네 모서리가 빠져
 * 화면에 보이는 장소가 추천 대상에서 누락된다(FR-002).
 *
 * **서버가 상태를 갖지 않는다.** 「이미 본 장소」는 클라이언트가 소유해 요청마다 보낸다 —
 * 「봤다」의 판정이 마커로 실제 그려졌는지이고, 격자로 솎아 그리는 쪽만 그것을 안다.
 */
@Service
class MapRecommendationService(
    private val mapQueryPort: MapQueryPort,
    private val spotQueryPort: SpotQueryPort,
    // 취향 유사도만 pgvector 생 SQL이라 포트가 갈린다 — SPEC-0035와 같은 선례다.
    private val tasteSimilarityQueryPort: TasteSimilarityQueryPort,
    private val eventPublisher: TourismEventPublisherPort,
    private val viewportPolicy: MapViewportPolicy,
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
        if (bounds.areaInSquareDegrees > viewportPolicy.maxAreaInSquareDegrees) {
            throw BboxTooLargeException("지도를 확대해 주세요.")
        }
        val first = query.first.coerceIn(1, viewportPolicy.maxRecommendationSize)
        // 좌표를 받는 연산이라 조회 사실을 남긴다(POL-045·057). **영역은 넘기지 않는다** —
        // 「현재 위치」 버튼을 누르면 영역이 실제 위치와 일치하기 때문이다.
        eventPublisher.publish(LocationAccessed(memberId, OPERATION))

        // 반응 제외는 DB 단계에서, 활성 언어 단위로 — `spots`·`recommendedSpots`와 같은 규칙이다.
        // 같은 반응이 취향의 재료이기도 하다.
        val reactions = spotQueryPort.findReactedSpotIdsByReaction(memberId, locale)
        val liked = reactions[ReactionType.LIKE].orEmpty()
        val disliked = reactions[ReactionType.DISLIKE].orEmpty()

        val candidates = mapQueryPort.findRecommendationCandidatesWithin(
            MapCandidateSearch(
                language = locale,
                bounds = bounds,
                categories = query.categories,
                excludedSpotIds = liked + disliked + query.excludedSpotIds,
                limit = viewportPolicy.recommendationCandidateCap,
            ),
        )
        val taste = tasteOf(liked, disliked, candidates, locale)
        val byId = candidates.associateBy { it.id }
        val ranked = candidates.map {
            RankedSpot(
                it.id,
                it.category,
                recommendationPolicy.score.score(it.distanceMeters, it.title, taste[it.id]),
            )
        }
        return recommendationPolicy.diversity.order(ranked).take(first).map { byId.getValue(it).toView() }
    }

    /**
     * 후보별 취향 요인. 반응이 최소량 미만이면 비어 있다. 좋아요 평균이 없으면(벡터 없음) 그 후보도 없음.
     *
     * `RecommendedSpotsQueryService`에 거의 같은 함수가 있다. 지금은 둘로 두는 편이 곧게 읽히지만,
     * **세 번째 호출자가 생기면 후보 id만 받는 공통 함수로 추출한다** — 그때는 실패 정책이 하나라는
     * 것이 우연이 아니라 규칙이 된다.
     */
    private fun tasteOf(
        liked: Set<Long>,
        disliked: Set<Long>,
        candidates: List<MapCandidateView>,
        locale: DomainLocale,
    ): Map<Long, Double> {
        if (candidates.isEmpty() ||
            liked.size + disliked.size < recommendationPolicy.tasteMinReactions
        ) {
            return emptyMap()
        }
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
        // 후보 집단 안에서 0~1로 펼친다 — 원값은 0.5 근처로 압축돼 거리에 묻힌다.
        return TasteNormalization.normalize(
            similarities.mapNotNull { (id, s) ->
                s.like?.let { like -> id to recommendationPolicy.score.tasteFactor(like, s.dislike) }
            }.toMap(),
        )
    }

    private fun MapCandidateView.toView(): MapSpotView = MapSpotView(
        id = id,
        title = title,
        thumbnailImageUrl = thumbnailImageUrl,
        category = category,
        lng = lng,
        lat = lat,
    )

    private companion object {
        /** 감사 기록의 연산명. 좌표 없이 이것만 남는다. */
        const val OPERATION = "mapRecommendedSpots"
    }
}
