package org.around.ieuri.tourism.domain.region

import org.around.ieuri.tourism.domain.region.RegionScore
import org.around.ieuri.tourism.domain.region.RegionScoreWeights
import org.around.ieuri.tourism.domain.region.RegionTotalScore

/** 항목별 점수를 총점으로 합치는 계산기(SPEC-0015). */
class RegionScoreCalculator(
    private val weights: RegionScoreWeights,
    private val minFactorCount: Int,
) {
    init {
        require(minFactorCount >= 1) { "최소 항목 수는 1 이상이어야 합니다: $minFactorCount" }
    }

    /**
     * 산출 가능했던 항목들로 총점을 낸다. 분모는 존재하는 항목의 가중치 합이다.
     * - 항목 수가 [minFactorCount] 미만(같으면 통과)이거나 가중치 합이 0이면 null이다.
     */
    fun calculate(factorScores: List<RegionScore>): RegionTotalScore? {
        val present = factorScores.distinctBy { it.factor }
        if (present.size < minFactorCount) return null

        val weightSum = present.sumOf { weights[it.factor] }
        if (weightSum <= 0.0) return null

        val weighted = present.sumOf { it.value * weights[it.factor] }
        val total = (weighted / weightSum).coerceIn(RegionScore.MIN_VALUE, RegionScore.MAX_VALUE)
        return RegionTotalScore(total, present)
    }
}
