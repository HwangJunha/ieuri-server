package org.around.ieuri.tourism.application.region.required.dto

import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.Coordinates

/** 추천 후보 시군구 1건. [center]는 기하학적 중심이 아니라 배치가 골라 둔 대표 위치다. */
data class RegionRow(
    val code: String,
    val provinceCode: String,
    val name: AreaName,
    val provinceName: AreaName,
    val center: Coordinates,
)
