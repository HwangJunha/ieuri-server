package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
/**
 * 반경 조회 아웃바운드 포트(PostGIS).
 * - 조회와 카운트는 같은 조건을 써야 한다 — 갈리면 hasNext와 totalCount가 어긋난다.
 */
interface SpotSpatialQueryPort {
    /**
     * 반경 안의 관광지를 거리 오름차순으로 한 페이지 조회한다. 적재된 카탈로그·번역만 본다.
     * - [SpotView.distanceMeters]는 DB가 계산한 구면 거리다.
     */
    fun searchNearby(query: NearbySpotSearch): List<SpotView>

    /** [query] 조건에 맞는 전체 건수(페이지 무관). SpotPage.totalCount 산출용. */
    fun countNearby(query: NearbySpotSearch): Int
}
