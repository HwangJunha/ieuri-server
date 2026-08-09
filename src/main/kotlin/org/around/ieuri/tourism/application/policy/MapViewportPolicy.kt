package org.around.ieuri.tourism.application.policy

/**
 * 지도 뷰포트 조회 정책값(SPEC-0014 R2~R5, 설정에서 주입).
 *
 * 여기 담긴 수치는 전부 **spec §8의 `[TBD]`** 이며 실측으로 확정한다. 기본값은 카카오맵
 * 줌 규약과 서울 면적을 근거로 잡은 출발점이지 확정값이 아니다 — 설정으로 빼 둔 이유가
 * 그것이다(코드 수정 없이 조정).
 *
 * @param minZoom 카카오맵 유효 줌 하한. 범위 밖은 INVALID_ZOOM.
 * @param maxZoom 카카오맵 유효 줌 상한.
 * @param clusterZoomThreshold 이 줌 **미만**이면 클러스터링한다(R2). 이상이면 개별 마커다.
 *   둘은 상호배타적이다.
 * @param maxAreaInSquareDegrees 뷰포트 면적 상한(위경도 도 곱, R5). 초과 시 BBOX_TOO_LARGE.
 *   전 지구 bbox 한 번으로 전체 데이터를 긁어가는 것을 막는 것이 목적이라 정밀할 필요가 없다.
 *   기본값 4.0은 대략 2°×2°로, 서울(약 0.3°×0.25°)을 한참 넘고 남한 전체를 담는 크기다.
 * @param maxSpotMarkers 개별 마커 상한(R3). 초과분은 잘리고 truncated가 선다.
 * @param maxClusters 클러스터 상한(R3). 셀 수가 이보다 많으면 잘린다.
 * @param maxEventMarkers 문화행사 마커 상한. 행사는 밀집도가 낮아 spot보다 작게 잡는다.
 * @param clusterGridSizeAtMinZoom 최저 줌에서의 그리드 셀 크기(도). 줌이 1 오를 때마다
 *   절반이 된다 — 카카오맵 줌이 1 오르면 축척이 2배가 되는 규약과 맞춘다.
 */
data class MapViewportPolicy(
    val minZoom: Int,
    val maxZoom: Int,
    val clusterZoomThreshold: Int,
    val maxAreaInSquareDegrees: Double,
    val maxSpotMarkers: Int,
    val maxClusters: Int,
    val maxEventMarkers: Int,
    val clusterGridSizeAtMinZoom: Double,
) {
    /** 줌이 카카오맵 유효 범위 안인지. */
    fun isValidZoom(zoom: Int): Boolean = zoom in minZoom..maxZoom

    /** 이 줌에서 개별 마커 대신 클러스터를 주는지(R2). */
    fun shouldCluster(zoom: Int): Boolean = zoom < clusterZoomThreshold

    /**
     * 이 줌의 그리드 셀 크기(도). 줌이 1 오를 때마다 절반이라 저줌일수록 크게 묶인다.
     *
     * [clusterZoomThreshold] 미만에서만 쓰이므로 지수가 커져 셀이 미세해질 일은 없다.
     */
    fun gridSizeFor(zoom: Int): Double {
        val steps = (zoom - minZoom).coerceAtLeast(0)
        return clusterGridSizeAtMinZoom / Math.pow(2.0, steps.toDouble())
    }
}
