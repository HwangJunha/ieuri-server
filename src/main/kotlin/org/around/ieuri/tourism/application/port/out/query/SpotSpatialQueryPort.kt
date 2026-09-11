package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DistanceCursor
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 반경 조회 아웃바운드 포트. **생 SQL 어댑터가 이 포트 뒤에 있다** — PostGIS 함수
 * (`ST_DWithin`·`ST_Distance`·`ST_MakePoint`)를 QueryDSL로 표현할 수 없기 때문이다.
 *
 * [SpotQueryPort]와 갈라 두는 이유는 `infrastructure → infrastructure` 주입을 막기 위해서다.
 * 어댑터가 다른 어댑터를 주입받으면 "어느 경로가 생 SQL을 쓰는가"가 애플리케이션에서 보이지 않는다.
 *
 * 조회와 카운트가 **같은 조건**을 써야 하므로 둘이 한 포트에 있다 — 갈리면 hasNext와 totalCount가
 * 어긋난다.
 */
interface SpotSpatialQueryPort {
    /**
     * 반경 안의 관광지를 **거리 오름차순**으로 한 페이지 조회한다. TourAPI를 호출하지 않고 적재된
     * 카탈로그·번역만 본다. [SpotSearchResult.distanceMeters]는 DB가 계산한 구면 거리다.
     */
    fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult>

    /** [query] 조건에 맞는 전체 건수(페이지 무관). SpotPage.totalCount 산출용. */
    fun countNearby(query: NearbySpotSearch): Int

    /**
     * [searchNearby]와 같은 조건·같은 정렬을 (거리, id) 키셋으로 이어 받는다(SPEC-0035 R3).
     * **`limit + 1`건을 돌려준다** — 초과분의 존재가 다음 페이지의 존재다.
     */
    fun searchNearbyAfter(query: NearbyKeysetSearch): List<SpotSearchResult>
}

/**
 * 키셋 반경 조회 입력. [after]가 null이면 첫 페이지다. 제외 집합의 의미는 [NearbySpotSearch]와 같다.
 */
data class NearbyKeysetSearch(
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    val categories: CategorySelection,
    val excludedSpotIds: Set<Long>,
    val limit: Int,
    val after: DistanceCursor?,
)
