package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.tourism.application.query.MapRecommendedSpotsQuery
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/** 지도 영역 추천 유스케이스(SPEC-0036). 회원 전용이라 비로그인 가능한 [MapQueryUseCase]와 갈라 둔다. */
interface MapRecommendationUseCase {
    /**
     * 보고 있는 영역 안의 추천 마커. 점수 내림차순, 동점은 id 오름차순이다.
     * - 거리 기준점은 영역 중심점이다(FR-002). 요청이 넘긴 제외 id는 후보에서 빠진다.
     * - 요청 크기보다 적으면 영역 안 후보가 소진된 것이다. 빈 결과는 오류가 아니다.
     */
    fun mapRecommendedSpots(
        query: MapRecommendedSpotsQuery,
        locale: DomainLocale,
        memberId: Long,
    ): List<MapSpotView>
}
