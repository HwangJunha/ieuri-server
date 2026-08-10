package org.around.ieuri.tourism.domain.vo

/**
 * 항목별 가중치(SPEC-0015 R12). 실측 근거가 없어 설정에서 주입되는 미결값이다(POL-044 · §3.3).
 *
 * 누락된 항목의 가중치는 [DEFAULT_WEIGHT]다 — 가중치 부재는 "그 항목을 쓰지 않겠다"가 아니라
 * 설정이 덜 채워졌다는 뜻이므로, 항목을 조용히 죽이는 0보다 균등 가중이 안전하다.
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

        /** 다섯 항목 균등 가중. 설정이 확정되기 전의 출발점이며 상수 정책이 아니다. */
        fun uniform(): RegionScoreWeights =
            RegionScoreWeights(RegionScoreFactor.entries.associateWith { DEFAULT_WEIGHT })
    }
}
