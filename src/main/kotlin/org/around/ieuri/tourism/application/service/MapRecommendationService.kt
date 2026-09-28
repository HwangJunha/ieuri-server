package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.exception.BboxTooLargeException
import org.around.ieuri.tourism.application.policy.MapViewportPolicy
import org.around.ieuri.tourism.application.policy.RecommendationPolicy
import org.around.ieuri.tourism.application.port.`in`.query.MapRecommendationUseCase
import org.around.ieuri.tourism.application.port.out.query.MapCandidateSearch
import org.around.ieuri.tourism.application.port.out.query.MapCandidateView
import org.around.ieuri.tourism.application.port.out.query.MapQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.TasteSimilarityQueryPort
import org.around.ieuri.tourism.application.query.MapRecommendedSpotsQuery
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.application.validation.boundsOf
import org.around.ieuri.tourism.domain.strategy.RankedSpot
import org.around.ieuri.tourism.domain.strategy.ScoreReaction
import org.around.ieuri.tourism.domain.strategy.TasteNormalization
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 지도 영역 추천(SPEC-0036). 보고 있는 영역 안 후보에서 이미 표시한 장소만 빼고,
 * 점수(거리·취향·면세·반응)를 매긴 뒤 카테고리 반복 감점으로 재배열해 앞 `first`건을 준다.
 *
 * **반응한 장소는 결과에서 빼지 않는다**(D-13). 지도는 화면 안에 있는 것을 그리는 필드이고
 * 추천은 그 안에서 순서를 정할 뿐이다 — 싫어요한 장소가 사라지면 화면 안인데 마커가 없는
 * 상태가 되며, 그것은 추천이 아니라 누락이다. 좋아요·싫어요는 배율로 순위에만 반영된다.
 *
 * **같은 좌표는 점수가 가장 높은 1건으로 접는다**(D-14). 지도는 좌표 하나에 마커 하나를
 * 그리므로, 한 지점에 여러 건을 실어 보내면 나머지는 응답 칸만 먹고 화면에는 존재하지 않는다 —
 * `first`가 「마커 N개」가 아니라 「행 N개」가 되어 화면이 빈다. 실측(가산, 회원 146): 접기 전
 * 상위 10건 중 4건이 현대아울렛 한 좌표였고, 접은 뒤 10건이 전부 다른 지점이 됐다.
 *
 * 점수 계산은 SPEC-0035가 정한 것을 그대로 쓴다 — 다른 것은 **거리 기준점이 영역 중심점**이고
 * 후보를 반경이 아니라 사각형으로 조달한다는 점뿐이다. 원으로 근사하면 네 모서리가 빠져
 * 화면에 보이는 장소가 추천 대상에서 누락된다(FR-002).
 *
 * **서버가 상태를 갖지 않는다.** 「이미 본 장소」는 클라이언트가 소유해 요청마다 보낸다 —
 * 「봤다」의 판정이 마커로 실제 그려졌는지이고, 격자로 솎아 그리는 쪽만 그것을 안다.
 */
@Service
@Transactional(readOnly = true)
class MapRecommendationService(
    private val mapQueryPort: MapQueryPort,
    private val spotQueryPort: SpotQueryPort,
    // 취향 유사도만 pgvector 생 SQL이라 포트가 갈린다 — SPEC-0035와 같은 선례다.
    private val tasteSimilarityQueryPort: TasteSimilarityQueryPort,
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

        // 반응은 활성 언어 단위로 읽는다 — `spots`·`recommendedSpots`와 같은 규칙이다.
        // **제외가 아니라 점수 배율과 취향의 재료로만 쓴다**(D-13).
        val reactions = spotQueryPort.findReactedSpotIdsByReaction(memberId, locale)
        val liked = reactions[ReactionType.LIKE].orEmpty()
        val disliked = reactions[ReactionType.DISLIKE].orEmpty()

        val candidates = mapQueryPort.findRecommendationCandidatesWithin(
            MapCandidateSearch(
                language = locale,
                bounds = bounds,
                categories = query.categories,
                // 제외는 클라이언트가 이미 그린 마커뿐이다 — 반응은 여기 들어오지 않는다(D-13).
                excludedSpotIds = query.excludedSpotIds,
                limit = viewportPolicy.recommendationCandidateCap,
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

    /**
     * 좌표가 같은 후보를 점수 최고 1건으로 접는다(D-14). 동점이면 id 오름차순 — 같은 요청이
     * 항상 같은 대표를 고르게 하기 위한 것이다(QR-001).
     *
     * **좌표를 반올림하지 않는다.** 「가까우면 같은 지점」은 격자 크기라는 근거 없는 값을
     * 요구하고, 줌에 따라 답이 달라진다. 겹쳐 그려지는 것이 확실한 것은 좌표가 **정확히**
     * 같은 경우뿐이고, 실제 데이터의 중복도 그 형태다(한 건물의 입점 브랜드가 같은 행 좌표를 공유).
     */
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
}
