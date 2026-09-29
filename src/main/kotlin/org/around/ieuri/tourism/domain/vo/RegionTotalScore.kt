package org.around.ieuri.tourism.domain.vo

/** 한 지역의 총점과 그 근거 항목들(SPEC-0015). 부재 항목은 0점으로 채우지 않고 빠진다(POL-043). */
data class RegionTotalScore(
    val total: Double,
    val factorScores: List<RegionScore>,
) {
    init {
        require(total in RegionScore.MIN_VALUE..RegionScore.MAX_VALUE) {
            "총점은 0~100 범위여야 합니다: $total"
        }
    }

    /** 총점 산출에 실제로 쓰인 항목 수. */
    val scoreFactorCount: Int get() = factorScores.size

    /** 그 항목이 산출됐는지. */
    fun hasFactor(factor: RegionScoreFactor): Boolean = factorScores.any { it.factor == factor }
}
