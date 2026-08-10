package org.around.ieuri.tourism.application.policy

/**
 * 지도 뷰포트 조회 정책값(SPEC-0014 R3~R5, 설정에서 주입).
 *
 * 여기 담긴 수치는 전부 **spec §8의 `[TBD]`** 이며 실측으로 확정한다. 기본값은 카카오맵
 * 줌 규약과 서울 면적을 근거로 잡은 출발점이지 확정값이 아니다 — 설정으로 빼 둔 이유가
 * 그것이다(코드 수정 없이 조정).
 *
 * @param minZoom 카카오맵 유효 줌 하한. 범위 밖은 INVALID_ZOOM.
 * @param maxZoom 카카오맵 유효 줌 상한.
 * @param maxAreaInSquareDegrees 뷰포트 면적 상한(위경도 도 곱, R5). 초과 시 BBOX_TOO_LARGE.
 *   전 지구 bbox 한 번으로 전체 데이터를 긁어가는 것을 막는 것이 목적이라 정밀할 필요가 없다.
 *   기본값 4.0은 대략 2°×2°로, 서울(약 0.3°×0.25°)을 한참 넘고 남한 전체를 담는 크기다.
 * @param maxSpotMarkers 개별 마커 상한(R3). 초과분은 잘리고 truncated가 선다.
 * @param maxEventMarkers 문화행사 마커 상한. 행사는 밀집도가 낮아 spot보다 작게 잡는다.
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
