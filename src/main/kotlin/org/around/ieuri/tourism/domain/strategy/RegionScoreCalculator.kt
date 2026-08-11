package org.around.ieuri.tourism.domain.strategy

import org.around.ieuri.tourism.domain.vo.RegionScore
import org.around.ieuri.tourism.domain.vo.RegionScoreWeights
import org.around.ieuri.tourism.domain.vo.RegionTotalScore

/**
 * 항목별 점수를 총점으로 합치는 계산기(SPEC-0015 R12 · R14 · R30).
 *
 * 정렬은 이 타입의 책임이 아니다.
 */
class RegionScoreCalculator(
    private val weights: RegionScoreWeights,
    private val minFactorCount: Int,
) {
    init {
        require(minFactorCount >= 1) { "최소 항목 수는 1 이상이어야 합니다: $minFactorCount" }
    }

    /**
     * 산출 가능했던 항목들로 총점을 낸다. 항목 수가 [minFactorCount] 미만이면 null이다
     * (R30) — **경계값(== 최소 개수)은 포함된다.** 가중치 합이 0일 때도 null이다.
     *
     * 분모는 **존재하는 항목의 가중치 합**이다 — 전체 항목 가중치 합으로 나누면 결측 지역의
     * 총점이 구조적으로 낮아진다.
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
