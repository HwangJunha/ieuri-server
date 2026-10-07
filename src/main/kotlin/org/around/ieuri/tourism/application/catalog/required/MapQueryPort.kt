package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.tourism.application.catalog.provided.dto.MapSpotView
import org.around.ieuri.tourism.application.catalog.required.dto.MapCandidateSearch
import org.around.ieuri.tourism.application.catalog.required.dto.MapCandidateView
import org.around.ieuri.tourism.application.catalog.required.dto.MapViewportSearch

/**
 * 지도 뷰포트 조회 아웃바운드 포트(SPEC-0014).
 * - bbox 경계선에 걸친 대상을 포함한다(`ST_Within`이 아니라 `ST_Intersects`).
 */
interface MapQueryPort {
    /** bbox 안의 개별 마커. `limit + 1`건까지 돌려준다 — 초과분은 호출자의 잘림 판정용이다. */
    fun findSpotsWithin(query: MapViewportSearch): List<MapSpotView>

    /**
     * bbox 안의 추천 후보(SPEC-0036). 제외 집합을 DB 단계에서 빼고 중심점 거리를 함께 돌려준다.
     * - 중심점에서 가까운 순이다. 최종 순서는 호출자가 점수로 재배열한다.
     */
    fun findRecommendationCandidatesWithin(query: MapCandidateSearch): List<MapCandidateView>
}
