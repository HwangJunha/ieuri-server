package org.around.ieuri.tourism.domain.footprint

import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.Coordinates

/**
 * 전국 지도의 표시 칸 하나(SPEC-0030 R7). 광역시·구를 가진 일반시는 시 단위로 1칸이다.
 * - [districtCodes]는 비어 있을 수 없다.
 */
data class MapCell(
    val id: Long,
    val districtCodes: Set<String>,
    val name: AreaName,
    val provinceName: AreaName,
    val provinceCode: String,
    val center: Coordinates,
) {
    init {
        require(districtCodes.isNotEmpty()) { "칸은 시군구를 하나 이상 포함해야 합니다." }
    }

    /** 이 칸을 방문했는가. 칸에 속한 시군구 중 하나라도 방문이면 참이다(R6). */
    fun isVisitedBy(visitedDistrictCodes: Set<String>): Boolean = districtCodes.any { it in visitedDistrictCodes }
}
