package org.around.ieuri.tourism.application.catalog

/**
 * 지도 뷰포트 조회 정책값(SPEC-0014, 설정에서 주입).
 * - 줌이 [minZoom]..[maxZoom] 밖이면 INVALID_ZOOM.
 * - 면적(위경도 도 곱)이 [maxAreaInSquareDegrees]를 넘으면 BBOX_TOO_LARGE.
 * - 마커 상한 초과분은 잘리고 truncated가 선다.
 */
data class MapViewportPolicy(
    val minZoom: Int,
    val maxZoom: Int,
    val maxAreaInSquareDegrees: Double,
    val maxSpotMarkers: Int,
    val maxEventMarkers: Int,
) {
    /** 줌이 카카오맵 유효 범위 안인지. */
    fun isValidZoom(zoom: Int): Boolean = zoom in minZoom..maxZoom
}
