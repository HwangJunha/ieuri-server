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

/**
 * 읽어 온 시군구 행을 칸으로 접는다(R7). 묶음 자체는 `district.cell_code`에 고정되어 있다.
 * - 정렬은 시도 코드 오름차순, 같은 시도 안에서는 칸 id 오름차순이다(POL-019).
 */
object MapCellGrouping {

    /**
     * 시군구 행을 칸으로 접는다. 같은 칸 id를 가진 행이 한 칸이 된다.
     * - 칸의 명칭·시도·대표 좌표는 그 칸에서 코드가 가장 작은 시군구의 값을 쓴다.
     */
    fun fold(rows: List<MapCellDistrictRow>): List<MapCell> = rows.groupBy { it.cellId }
        .map { (cellId, group) ->
            val representative = group.minBy { it.districtCode }
            MapCell(
                id = cellId,
                districtCodes = group.mapTo(LinkedHashSet()) { it.districtCode },
                name = representative.cellName,
                provinceName = representative.provinceName,
                provinceCode = representative.provinceCode,
                center = representative.center,
            )
        }
        .sortedWith(compareBy({ it.provinceCode }, { it.id }))
}

/**
 * 칸을 만들기 위해 읽어 온 시군구 한 행.
 * - [cellName]은 시군구 이름이 아니라 칸 이름이다(예: '서울특별시', '수원시').
 */
data class MapCellDistrictRow(
    val cellId: Long,
    val districtCode: String,
    val cellName: AreaName,
    val provinceName: AreaName,
    val provinceCode: String,
    val center: Coordinates,
)
