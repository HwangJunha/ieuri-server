package org.around.ieuri.tourism.domain.strategy

import org.around.ieuri.tourism.domain.vo.RegionScore
import org.around.ieuri.tourism.domain.vo.RegionScoreWeights
import org.around.ieuri.tourism.domain.vo.RegionTotalScore

/**
 * 항목별 점수를 총점으로 합치는 계산기(SPEC-0015 R12 · R14 · R30 · 설계 §3.4~§3.5).
 *
 * [weights]와 [minFactorCount]는 전부 설정 주입값이다 — 실측 근거 없는 계수를 코드에 박지
 * 않는다(POL-044).
 *
 * 정렬은 이 타입의 책임이 아니다. **순서는 총점 단일 기준이며 항목 수를 섞지 않는다**(R16) —
 * 섞으면 "덜 알려진 지역이 무조건 밀린다"는 다른 편향이 생긴다.
 */
class RegionScoreCalculator(
    private val weights: RegionScoreWeights,
    private val minFactorCount: Int,
) {
    init {
        require(minFactorCount >= 1) { "최소 항목 수는 1 이상이어야 합니다: $minFactorCount" }
    }

    /**
     * 산출 가능했던 항목들로 총점을 낸다. 항목 수가 [minFactorCount] 미만이면 추천 대상이
     * 아니므로 null이다(R30) — **경계값(== 최소 개수)은 포함된다.**
     *
     * 분모는 **존재하는 항목의 가중치 합**이다. 다섯 항목 가중치 합으로 나누면 결측 지역의
     * 총점이 구조적으로 낮아져, 0으로 채우지 않기로 한 결정이 분모에서 되살아난다(§3.4).
     *
     * 가중치 합이 0이면(전 항목 가중치 0) 나눌 수 없어 null이다.
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
