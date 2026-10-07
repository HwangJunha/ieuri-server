package org.around.ieuri.tourism.application.footprint.provided.dto

import org.around.ieuri.tourism.domain.shared.Coordinates

/** 전국 지도 칸 1개(SPEC-0030). [name]·[provinceName]은 이미 활성 언어로 해소된 값이다. */
data class MapCellView(
    val id: Long,
    val districtCodes: List<String>,
    val name: String,
    val provinceName: String,
    val provinceCode: String,
    val center: Coordinates,
    val viewerVisited: Boolean,
)
