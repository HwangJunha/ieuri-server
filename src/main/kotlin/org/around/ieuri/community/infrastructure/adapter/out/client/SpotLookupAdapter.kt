package org.around.ieuri.community.infrastructure.adapter.out.client

import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.infrastructure.adapter.out.client.mapper.toPostSpotView
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

    override fun findSummary(spotId: Long): PostSpotView? =
        spotQueryUseCase.spotSummary(spotId)?.toPostSpotView()

    /**
     * 여러 spot을 **한 번의 조회로** 가져온다. 존재하지 않는 spot은 결과에서 빠진다.
     *
     * 행마다 findSummary를 부르지 않는다 — 지역 피드는 대상이 수천 건일 수 있어
     * 그렇게 하면 쿼리가 그 수만큼 나간다.
     */
    override fun findSummaries(spotIds: List<Long>): Map<Long, PostSpotView> =
        spotQueryUseCase.spotSummaries(spotIds.distinct())
            .mapValues { (_, summary) -> summary.toPostSpotView() }
}
