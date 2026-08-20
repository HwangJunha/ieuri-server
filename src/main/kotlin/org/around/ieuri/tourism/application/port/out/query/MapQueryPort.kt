package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 지도 뷰포트 조회 아웃바운드 포트(SPEC-0014 R1~R3). bbox는 PostGIS `ST_Intersects`로 좁히며
 * **경계선에 걸친 대상을 포함한다**(R1) — `ST_Within`은 경계선상 점을 제외한다.
 */
interface MapQueryPort {
    /**
     * bbox 안의 개별 마커. **`limit + 1`건까지 돌려준다** — 초과분은 호출자의 잘림 판정용이며
     * 응답에 싣지 않는다. `COUNT(*)`를 따로 세지 않는 이유는 정확한 총계가 필요 없고
     * "더 있다"만 알면 되기 때문이다(R3).
     */
    fun findSpotsWithin(query: MapViewportSearch): List<MapSpotView>

}

/**
 * 구역 메타데이터 조회 아웃바운드 포트(R6·R16·R17). 실시간 값은 여기서 오지 않는다 —
 * 정적 메타데이터와 경계만 담당한다.
 */
interface CongestionAreaQueryPort {
    /** 121곳 전체의 경계와 메타데이터(R17). 활성 언어로 이름을 고른 뒤 돌려준다. */
    fun findAllBoundaries(locale: DomainLocale): List<CongestionAreaBoundaryView>

    /**
     * bbox에 걸치는 구역의 코드(R6). 폴리곤이 bbox와 교차하면 포함이다.
     * 뷰포트에 걸치는 구역만 업스트림에 물어보기 위한 것이라 호출량 전략의 핵심이다.
     */
    fun findAreaCodesWithin(bounds: MapBounds): List<String>

    /**
     * 주어진 코드 중 실재하는 구역의 이름. 존재하지 않는 코드는 결과에서 빠지므로
     * 호출자가 `CONGESTION_AREA_NOT_FOUND`를 판정할 수 있다(R6).
     */
    fun findNamesByCodes(areaCodes: List<String>): Map<String, AreaName>
}

/**
 * bbox 마커 조회 조건. [limit]은 상한 + 1로 넘어온다(호출자가 잘림을 판정한다).
 *
 * **언어 필터가 있다** — `spot`은 같은 장소가 언어별로 다른 행이라, 걸지 않으면 같은
 * 마커가 4배로 나온다.
 */
data class MapViewportSearch(
    val language: DomainLocale,
    val bounds: MapBounds,
    /** 카테고리 선택. 비어 있으면 전체. 값이 여럿이면 IN으로 거른다(OR). 상한보다 먼저 적용된다. */
    val categories: CategorySelection,
    val limit: Int,
)

