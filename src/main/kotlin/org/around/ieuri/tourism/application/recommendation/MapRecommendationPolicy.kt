package org.around.ieuri.tourism.application.recommendation

/**
 * 지도 영역 추천 정책값(SPEC-0036, 설정에서 주입).
 * - 면적(위경도 도 곱)이 [maxAreaInSquareDegrees]를 넘으면 BBOX_TOO_LARGE — 뷰포트와 같은 설정값이다.
 * - [candidateCap]은 중심점에서 가까운 순 점수 계산 대상 수다(SPEC-0035의 candidate-cap과 별개).
 * - [maxSize] 초과 요청은 거절이 아니라 절삭이다(POL-018).
 */
data class MapRecommendationPolicy(
    val maxAreaInSquareDegrees: Double,
    val candidateCap: Int,
    val maxSize: Int,
) {
    init {
        require(candidateCap >= 1) { "추천 후보 상한은 1 이상이어야 합니다." }
        require(maxSize >= 1) { "추천 묶음 크기 상한은 1 이상이어야 합니다." }
    }
}
