package org.around.ieuri.tourism.domain.vo

/**
 * 한 지역의 총점과 그 근거가 된 항목들(SPEC-0015 R12 · R17 · R31).
 *
 * [factorScores]에는 **산출 가능했던 항목만** 들어간다 — 부재 항목은 0점으로 채우지 않고
 * 빠진다(POL-043). 따라서 [scoreFactorCount]가 지역마다 다를 수 있고, 그 사실을 감추지 않고
 * 계약에 노출한다.
 */
data class RegionTotalScore(
    val total: Double,
    val factorScores: List<RegionScore>,
) {
    init {
        require(total in RegionScore.MIN_VALUE..RegionScore.MAX_VALUE) {
            "총점은 0~100 범위여야 합니다: $total"
        }
    }

    /** 총점 산출에 실제로 쓰인 항목 수(R31). */
    val scoreFactorCount: Int get() = factorScores.size

    /** 그 항목이 산출됐는지. 부재는 결과에서 부재로 표기된다(R14). */
    fun hasFactor(factor: RegionScoreFactor): Boolean = factorScores.any { it.factor == factor }
}
