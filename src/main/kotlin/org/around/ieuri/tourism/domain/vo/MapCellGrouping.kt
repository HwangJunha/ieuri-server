package org.around.ieuri.tourism.domain.vo

/**
 * 전국 지도의 표시 칸 하나(SPEC-0030 R7).
 *
 * 행정구역 시군구와 1:1이 아니다 — 광역시는 전체가 1칸, 구를 가진 일반시 12곳도 시 단위로
 * 1칸, 그 외에는 시군 1개가 1칸이다. 전국 252개 시군구가 161칸이 된다.
 *
 * [districtCodes]가 이 칸에 묶인 시군구다. 방문 판정([isVisitedBy])과 spot 귀속이 모두 이
 * 집합을 통해 이뤄지므로 **비어 있을 수 없다** — 시군구가 하나도 없는 칸은 칸이 아니다.
 */
data class MapCell(
    val code: String,
    val districtCodes: Set<String>,
    val name: AreaName,
    val provinceName: AreaName,
    val provinceCode: String,
    val center: Coordinates,
) {
    init {
        require(code.isNotBlank()) { "칸 코드는 비어 있을 수 없습니다." }
        require(districtCodes.isNotEmpty()) { "칸은 시군구를 하나 이상 포함해야 합니다." }
    }

    /**
     * 이 칸을 방문했는가. **칸에 속한 시군구 중 하나라도** 방문이면 참이다 —
     * 수원시 장안구를 방문하면 수원시 칸 전체가 방문으로 표시된다(R6).
     */
    fun isVisitedBy(visitedDistrictCodes: Set<String>): Boolean =
        districtCodes.any { it in visitedDistrictCodes }
}

/**
 * 252개 시군구를 161칸으로 묶는 규칙(R7).
 *
 * **묶음 자체는 `district.cell_code`에 고정되어 있고**(V74) 이 객체는 그렇게 읽어 온 행을
 * 칸으로 접는 일만 한다. 규칙을 두 곳에 두지 않으려는 것이며, 매핑이 질의 조건이라
 * 런타임 파싱으로는 인덱스를 탈 수 없다는 이유가 그 결정의 근거다.
 *
 * 정렬은 **시도 코드 오름차순, 같은 시도 안에서는 칸 코드 오름차순**으로 고정이다(POL-019).
 */
object MapCellGrouping {

    /**
     * 시군구 행을 칸으로 접는다. 같은 `cellCode`를 가진 행이 한 칸이 된다.
     *
     * 칸의 명칭·시도·대표 좌표는 **그 칸에서 코드가 가장 작은 시군구의 값**을 쓴다. 광역시
     * 칸이라면 '서울특별시'처럼 시도 이름을 별도로 넘겨받아야 옳지만, 적재가 대표 시군구의
     * 이름을 그대로 칸 이름으로 삼는 것을 전제한다(§adapter).
     */
    fun fold(rows: List<MapCellDistrictRow>): List<MapCell> =
        rows.groupBy { it.cellCode }
            .map { (cellCode, group) ->
                val representative = group.minBy { it.districtCode }
                MapCell(
                    code = cellCode,
                    districtCodes = group.mapTo(LinkedHashSet()) { it.districtCode },
                    name = representative.cellName,
                    provinceName = representative.provinceName,
                    provinceCode = representative.provinceCode,
                    center = representative.center,
                )
            }
            .sortedWith(compareBy({ it.provinceCode }, { it.code }))
}

/**
 * 칸을 만들기 위해 읽어 온 시군구 한 행.
 *
 * [cellName]은 **시군구 이름이 아니라 칸 이름이다** — 광역시 칸이면 '서울특별시', 일반시
 * 칸이면 '수원시'이며, 그 판단은 조회 어댑터가 한다(칸 코드 길이가 판단 근거다).
 */
data class MapCellDistrictRow(
    val cellCode: String,
    val districtCode: String,
    val cellName: AreaName,
    val provinceName: AreaName,
    val provinceCode: String,
    val center: Coordinates,
)
