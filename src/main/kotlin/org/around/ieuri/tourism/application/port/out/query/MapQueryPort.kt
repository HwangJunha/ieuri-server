package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotCluster

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

    /**
     * bbox 안의 spot을 그리드 셀로 묶은 클러스터(R2). 집계는 DB에서 수행한다 — 전체를
     * 애플리케이션으로 끌어와 묶으면 저줌에서 상한의 의미가 사라진다.
     *
     * 중심은 셀 중심이 아니라 **포함된 spot 좌표의 평균**이다. [findSpotsWithin]과 같이
     * `limit + 1`건까지 돌려준다.
     */
    fun findClustersWithin(query: MapClusterSearch): List<SpotCluster>
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
    val category: SpotCategory?,
    val limit: Int,
)

/**
 * 클러스터 조회 조건. [gridSizeInDegrees]는 줌에서 유도한 셀 크기이며, 어댑터는 좌표를
 * 이 크기로 나눠 셀에 배정한다.
 */
data class MapClusterSearch(
    val language: DomainLocale,
    val bounds: MapBounds,
    val category: SpotCategory?,
    val gridSizeInDegrees: Double,
    val limit: Int,
)
