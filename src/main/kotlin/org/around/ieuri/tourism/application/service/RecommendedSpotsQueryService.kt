package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.tourism.application.policy.RecommendationPolicy
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.RecommendedSpotsUseCase
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotCandidate
import org.around.ieuri.tourism.application.port.out.query.SpotCard
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
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
 * 내 주변 추천(SPEC-0035). 반경 안 가까운 순 N건에서 이 회원이 반응한 장소를 빼고, 점수(거리·취향·면세)를
 * 매긴 뒤 카테고리 반복 감점으로 재배열해 앞 `first`건을 준다. 요청마다 다시 계산하며 서버 상태가 없다.
 *
 * 취향은 회원의 좋아요·싫어요 장소 벡터 평균과 후보의 코사인이다(S2). 반응이 최소량 미만이거나 벡터가
 * 없으면 그 후보는 거리만으로 점수를 받는다. 유사도 조회 실패는 오류가 아니라 취향 없음이다.
 */
@Service
@Transactional(readOnly = true)
class RecommendedSpotsQueryService(
    private val spotQueryPort: SpotQueryPort,
    // 반경 조회만 PostGIS 생 SQL이라 포트가 갈린다 — 어느 읽기가 그쪽인지 여기서 보인다.
    private val spotSpatialQueryPort: SpotSpatialQueryPort,
    // 취향 유사도만 pgvector 생 SQL이라 포트가 갈린다.
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
        // 좌표를 받는 연산이므로 조회 사실을 남긴다(SPEC-0032 R19). **좌표는 넘기지 않는다.**
        eventPublisher.publish(LocationAccessed(memberId, "recommendedSpots"))

        // 반응 제외는 DB 단계에서, 활성 언어 단위로 — `spots`와 같은 규칙이다. 같은 반응이 취향의 재료다.
        val reactions = spotQueryPort.findReactedSpotIdsByReaction(memberId, locale)
        val liked = reactions[ReactionType.LIKE].orEmpty()
        val disliked = reactions[ReactionType.DISLIKE].orEmpty()
        // **후보는 썸네일·저작권 없이 받는다**(SPEC-0038). 그 둘만 카탈로그에 있어, 후보
        // 전부에 조인하면 아래 재배열에서 버려질 행까지 읽는다 — cap 500·first 100이면
        // 400건분이 낭비다. 살아남은 것만 아래에서 채운다.
        val candidates = spotSpatialQueryPort.searchNearbyCandidates(
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
        val survivors = policy.diversity.order(ranked).take(first)
        // 카드 필드는 여기서 채운다. **없는 id는 건너뛴다** — 조회 사이에 삭제된 장소를
        // 빈 카드로 내보내면 클라이언트가 non-null 필드에서 깨진다.
        val cards = spotSpatialQueryPort.findCards(survivors)
        return survivors.mapNotNull { id ->
            cards[id]?.let { byId.getValue(id).toView(it) }
        }
    }

    /** 후보별 취향 요인. 반응이 최소량 미만이면 비어 있다. 좋아요 평균이 없으면(벡터 없음) 그 후보도 없음. */
    private fun tasteOf(
        liked: Set<Long>,
        disliked: Set<Long>,
        candidates: List<SpotCandidate>,
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
        // 후보 집단 안에서 0~1로 펼친다 — 원값은 0.5 근처로 압축돼 거리에 묻힌다.
        return TasteNormalization.normalize(
            similarities.mapNotNull { (id, s) ->
                s.like?.let { like -> id to policy.score.tasteFactor(like, s.dislike) }
            }.toMap(),
        )
    }

    /** attributionText는 copyright에서 파생된다 — 적재하지 않는 이유이기도 하다(둘이 어긋날 수 있다). */
    private fun SpotCandidate.toView(card: SpotCard): SpotView = SpotView(
        id = spotId,
        title = title,
        address = address,
        thumbnailImageUrl = card.thumbnailImageUrl,
        distanceMeters = distanceMeters,
        category = category,
        copyright = card.copyright,
        attributionText = card.copyright.attributionText,
        mapX = mapX,
        mapY = mapY,
    )
}
