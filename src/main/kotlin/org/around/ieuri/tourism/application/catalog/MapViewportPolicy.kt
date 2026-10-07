package org.around.ieuri.tourism.application.catalog

/**
 * 지도 뷰포트 조회 정책값(SPEC-0014, 설정에서 주입).
 * - 줌이 [minZoom]..[maxZoom] 밖이면 INVALID_ZOOM.
 * - 면적(위경도 도 곱)이 [maxAreaInSquareDegrees]를 넘으면 BBOX_TOO_LARGE.
 * - 마커 상한 초과분은 잘리고 truncated가 선다.
 * - [recommendationCandidateCap]은 중심점에서 가까운 순 점수 계산 대상 수다(SPEC-0035의 candidate-cap과 별개).
 * - [maxRecommendationSize] 초과 요청은 거절이 아니라 절삭이다(POL-018).
 */
data class MapViewportPolicy(
    val minZoom: Int,
    val maxZoom: Int,
    val maxAreaInSquareDegrees: Double,
    val maxSpotMarkers: Int,
    val maxEventMarkers: Int,
    val recommendationCandidateCap: Int,
    val maxRecommendationSize: Int,
) {
    init {
        require(recommendationCandidateCap >= 1) { "추천 후보 상한은 1 이상이어야 합니다." }
        require(maxRecommendationSize >= 1) { "추천 묶음 크기 상한은 1 이상이어야 합니다." }
    }

    /** 줌이 카카오맵 유효 범위 안인지. */
    fun isValidZoom(zoom: Int): Boolean = zoom in minZoom..maxZoom
}
