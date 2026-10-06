package org.around.ieuri.tourism.application.shared.required

/**
 * 반경 조회 아웃바운드 포트. 생 SQL(PostGIS) 어댑터가 이 포트 뒤에 있다.
 * - 조회와 카운트는 같은 조건을 써야 한다 — 갈리면 hasNext와 totalCount가 어긋난다.
 */
interface SpotSpatialQueryPort {
    /**
     * 반경 안의 관광지를 거리 오름차순으로 한 페이지 조회한다. 적재된 카탈로그·번역만 본다.
     * - [SpotSearchResult.distanceMeters]는 DB가 계산한 구면 거리다.
     */
    fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult>

    /** [query] 조건에 맞는 전체 건수(페이지 무관). SpotPage.totalCount 산출용. */
    fun countNearby(query: NearbySpotSearch): Int
}
