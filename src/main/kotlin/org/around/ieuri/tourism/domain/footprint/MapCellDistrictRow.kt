package org.around.ieuri.tourism.domain.footprint

import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.Coordinates

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
