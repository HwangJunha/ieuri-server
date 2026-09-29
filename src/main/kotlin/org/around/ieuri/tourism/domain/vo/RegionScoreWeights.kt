package org.around.ieuri.tourism.domain.vo

/**
 * 항목별 가중치(SPEC-0015 R12).
 * - 누락된 항목의 가중치는 0이 아니라 [DEFAULT_WEIGHT]다.
 */
data class RegionScoreWeights(
    private val weights: Map<RegionScoreFactor, Double>,
) {
    init {
        require(weights.values.all { it >= 0.0 }) { "가중치는 음수일 수 없습니다: $weights" }
    }

    operator fun get(factor: RegionScoreFactor): Double = weights[factor] ?: DEFAULT_WEIGHT

    companion object {
        const val DEFAULT_WEIGHT = 1.0

        /** 다섯 항목 균등 가중. */
        fun uniform(): RegionScoreWeights = RegionScoreWeights(RegionScoreFactor.entries.associateWith { DEFAULT_WEIGHT })
    }
}
