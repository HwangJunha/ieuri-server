package org.around.ieuri.tourism.application.policy

import org.around.ieuri.tourism.domain.vo.DistanceScoring
import org.around.ieuri.tourism.domain.vo.RegionScoreWeights

/**
 * 지역 추천 정책값(SPEC-0015 R19·R30, 설정에서 주입).
 *
 * 여기 담긴 계수는 전부 **실측 전 잠정값**이다(§3.3 · POL-044) — 코드 상수로 박지 않고 설정으로
 * 뺀 이유가 그것이다. 값이 바뀌어도 코드는 바뀌지 않는다.
 *
 * @param weights 항목별 가중치. 미지정 항목은 균등 가중이다.
 * @param distanceScoring 거리 감쇠. 반감 거리에서 거리 점수가 50점이 된다.
 * @param minFactorCount 추천 대상이 되기 위한 최소 산출 항목 수(R30). **경계값은 포함된다.**
 * @param defaultLimit limit 미지정 시 개수.
 * @param maxLimit 개수 상한(POL-018). **초과는 거절이 아니라 클램프**이며, 0 이하만 거절이다(R29).
 */
data class RegionRecommendationPolicy(
    val weights: RegionScoreWeights,
    val distanceScoring: DistanceScoring,
    val minFactorCount: Int,
    val defaultLimit: Int,
    val maxLimit: Int,
) {
    init {
        require(defaultLimit in 1..maxLimit) { "기본 개수는 1 이상 상한 이하여야 합니다: $defaultLimit" }
    }

    /** null(미지정)은 기본값, 상한 초과는 상한으로 자른다. 0 이하 판정은 호출자(어댑터)의 몫이다. */
    fun resolveLimit(requested: Int?): Int = (requested ?: defaultLimit).coerceAtMost(maxLimit)
}
