package org.around.ieuri.tourism.application.congestion.provided.dto

/**
 * 구역 1곳의 정적 경계.
 * - [polygon]은 `[[lng, lat], ...]`(GeoJSON 순서)의 닫힌 단일 폐곡선이며, 첫 점과 끝 점이 같다.
 */
data class CongestionAreaBoundaryView(
    val id: String,
    val name: String,
    val category: String,
    val polygon: List<List<Double>>,
)
