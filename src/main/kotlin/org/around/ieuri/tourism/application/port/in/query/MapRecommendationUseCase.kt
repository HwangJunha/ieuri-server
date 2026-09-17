package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.tourism.application.query.MapRecommendedSpotsQuery
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 지도 영역 추천 유스케이스(SPEC-0036).
 *
 * [MapQueryUseCase]와 갈라 두는 이유는 **인증 경계가 다르기** 때문이다 — 이쪽은 회원의 반응과
 * 취향을 쓰므로 로그인 필수이고, 뷰포트 마커·문화행사·혼잡도는 비로그인에서도 동작해야 한다.
 * 한 포트에 묶으면 회원 id가 필요 없는 메서드까지 회원 단위 포트에 들어간다.
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
