package org.around.ieuri.community.infrastructure.adapter.out.client

import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.query.PostTargetView
import org.around.ieuri.community.infrastructure.adapter.out.client.mapper.toPostTargetView
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.springframework.stereotype.Component

/**
 * spot 조회. `tourism`의 **인바운드 포트**를 통해서만 접근한다 — 도메인 내부(애그리게이트·
 * 영속 어댑터)를 직접 끌어다 쓰지 않으며, 그래야 tourism이 커뮤니티를 계속 모른 채로 있을 수 있다.
 *
 * community의 application·domain은 SpotLookupPort만 알고 tourism이라는 이름조차 모른다.
 *
 * `spotSummary`를 쓰는 이유는 `spotDetail`이 캐시 미스 시 TourAPI를 왕복하기 때문이다 —
 * 대상 유효성 확인 한 번에 외부 호출이 붙으면 글쓰기 지연이 업스트림에 묶인다.
 */
@Component
class SpotLookupAdapter(
    private val spotQueryUseCase: SpotQueryUseCase,
) : SpotLookupPort {

    override fun findSummary(spotId: Long): PostTargetView? =
        spotQueryUseCase.spotSummary(spotId)?.toPostTargetView()

    /** 반경 조건은 tourism이 계산한다 — `spot` 테이블을 아는 코드가 그쪽에 머물게 하려는 것이다. */
    override fun findIdsWithin(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        limit: Int,
    ): List<Long> = spotQueryUseCase.spotIdsWithin(
        longitude = longitude,
        latitude = latitude,
        radiusMeters = radiusMeters,
        limit = limit,
    )

    /** 존재하지 않는 spot은 결과에서 빠진다 — 호출자는 그것을 필터에서 제외한다. */
    override fun findSummaries(spotIds: List<Long>): Map<Long, PostTargetView> {
        val summaries = mutableMapOf<Long, PostTargetView>()
        for (spotId in spotIds.distinct()) {
            val summary = findSummary(spotId)
            if (summary != null) {
                summaries[spotId] = summary
            }
        }
        return summaries
    }
}
