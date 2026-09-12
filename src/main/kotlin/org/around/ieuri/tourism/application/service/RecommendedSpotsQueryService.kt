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
import org.around.ieuri.tourism.application.query.RecommendedSpotsQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.validation.coordinatesOf
import org.around.ieuri.tourism.application.validation.requireRadiusWithin
import org.around.ieuri.tourism.domain.strategy.RankedSpot
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.stereotype.Service

/**
 * 내 주변 추천(SPEC-0035). 반경 안 가까운 순 N건에서 이 회원이 반응한 장소를 빼고, 점수(거리·면세)를
 * 매긴 뒤 카테고리 반복 감점으로 재배열해 앞 `first`건을 준다. 요청마다 다시 계산하며 서버 상태가 없다.
 */
@Service
class RecommendedSpotsQueryService(
    private val spotQueryPort: SpotQueryPort,
    // 반경 조회만 PostGIS 생 SQL이라 포트가 갈린다 — 어느 읽기가 그쪽인지 여기서 보인다.
    private val spotSpatialQueryPort: SpotSpatialQueryPort,
    private val eventPublisher: TourismEventPublisherPort,
    private val spotQueryPolicy: SpotQueryPolicy,
    private val policy: RecommendationPolicy,
) : RecommendedSpotsUseCase {

    override fun recommendedSpots(query: RecommendedSpotsQuery, locale: DomainLocale, memberId: Long): List<SpotView> {
        val center = coordinatesOf(query.latitude, query.longitude)
        requireRadiusWithin(query.radiusMeters, spotQueryPolicy.maxRadiusMeters)
        val first = query.first.coerceIn(1, spotQueryPolicy.maxPageSize)
        // 좌표를 받는 연산이므로 조회 사실을 남긴다(SPEC-0032 R19). **좌표는 넘기지 않는다.**
        eventPublisher.publish(LocationAccessed(memberId, "recommendedSpots"))

        // 반응 제외는 DB 단계에서, 활성 언어 단위로 — `spots`와 같은 규칙이다.
        val candidates = spotSpatialQueryPort.searchNearby(
            NearbySpotSearch(
                language = locale,
                center = center,
                radiusMeters = query.radiusMeters,
                categories = query.categories,
                excludedSpotIds = spotQueryPort.findReactedSpotIds(memberId, locale),
                limit = policy.candidateCap,
                offset = 0,
            ),
        )
        val byId = candidates.associateBy { it.spotId }
        val ranked = candidates.map { RankedSpot(it.spotId, it.category, policy.score.score(it.distanceMeters, it.title)) }
        return policy.diversity.order(ranked).take(first).map { byId.getValue(it).toView() }
    }

    /** attributionText는 copyright에서 파생된다 — 적재하지 않는 이유이기도 하다(둘이 어긋날 수 있다). */
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
