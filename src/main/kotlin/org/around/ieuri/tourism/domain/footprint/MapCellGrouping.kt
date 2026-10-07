package org.around.ieuri.tourism.domain.footprint

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
