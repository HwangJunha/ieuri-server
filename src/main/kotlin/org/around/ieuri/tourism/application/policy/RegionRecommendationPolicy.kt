package org.around.ieuri.tourism.application.policy

import org.around.ieuri.tourism.domain.vo.DistanceScoring
import org.around.ieuri.tourism.domain.vo.FreshnessDecay
import org.around.ieuri.tourism.domain.vo.RegionScoreWeights

/**
 * 지역 추천 정책값(SPEC-0015 R19·R30, 설정에서 주입).
 *
 * @param weights 항목별 가중치. 미지정 항목은 균등 가중이다.
 * @param distanceScoring 거리 감쇠. 반감 거리에서 거리 점수가 50점이 된다.
 * @param freshnessDecay 오래된 지표의 점수 감쇠. **방문자수에만 적용한다.**
 * @param minFactorCount 최소 산출 항목 수(R30). **경계값은 포함되고, 거리도 한 항목으로
 *   세어진다** — 지표 4종은 5항목이다.
 * @param minDistrictsPerProvince 시도가 후보로 남기 위한 최소 시군구 수. 경계값은 포함된다.
 * @param defaultLimit limit 미지정 시 개수.
 * @param maxLimit 개수 상한(POL-018). **초과는 클램프**이며, 0 이하만 거절이다(R29).
 */
data class RegionRecommendationPolicy(
    val weights: RegionScoreWeights,
    val distanceScoring: DistanceScoring,
    val freshnessDecay: FreshnessDecay,
    val minFactorCount: Int,
    val minDistrictsPerProvince: Int,
    val defaultLimit: Int,
    val maxLimit: Int,
) {
    init {
        require(defaultLimit in 1..maxLimit) { "기본 개수는 1 이상 상한 이하여야 합니다: $defaultLimit" }
    }

    /** null(미지정)은 기본값, 상한 초과는 상한으로 자른다. 0 이하 판정은 호출자(어댑터)의 몫이다. */
    fun resolveLimit(requested: Int?): Int = (requested ?: defaultLimit).coerceAtMost(maxLimit)
}
