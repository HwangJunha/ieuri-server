package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.tourism.application.query.MapRecommendedSpotsQuery
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 지도 영역 추천 유스케이스(SPEC-0036).
 *
 * [MapQueryUseCase]와 갈라 두는 이유는 **인증 경계가 다르기** 때문이다. 한 포트에 묶으면
 * `MapQueryResolver`가 `CurrentMemberProvider`를 갖게 되고, 그 뒤로는 거기 필드를 더하는 사람이
 * 어느 메서드가 회원을 요구하는지 기억해야 한다 — 뷰포트 마커·문화행사·혼잡도는 비로그인에서도
 * 동작해야 하므로 그 경계를 구조로 남긴다. `RecommendedSpotsUseCase`가 `SpotQueryUseCase`와
 * 갈라진 것과 같은 선례다.
 */
interface MapRecommendationUseCase {
    /**
     * 보고 있는 영역 안의 추천 마커. 점수 내림차순, 동점은 id 오름차순이다.
     *
     * 거리 요인의 기준점은 **영역 중심점**이며 회원의 실제 위치가 아니다(FR-002).
     * 반응한 장소와 요청이 넘긴 제외 id는 후보에서 빠지고, 요청 크기보다 적게 돌아오면
     * 영역 안 후보가 소진된 것이다 — 빈 결과는 오류가 아니다.
     */
    fun mapRecommendedSpots(
        query: MapRecommendedSpotsQuery,
        locale: DomainLocale,
        memberId: Long,
    ): List<MapSpotView>
}
