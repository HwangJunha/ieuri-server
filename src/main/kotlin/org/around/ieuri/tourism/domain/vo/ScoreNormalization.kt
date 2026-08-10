package org.around.ieuri.tourism.domain.vo

/**
 * 한 항목의 정규화 기준 구간(SPEC-0015 R11 · 설계 §3.3).
 *
 * 기준을 **전국 분포 대비로 잡을지 기간 내 상대값으로 잡을지는 미결**이므로, 그 판단은
 * 경계값을 만들어 넣는 쪽이 하고 여기서는 받은 [min]~[max]만 쓴다. 두 방식 모두 이 타입으로
 * 표현되므로 확정 시 코드가 바뀌지 않는다(POL-044).
 */
data class ScoreNormalization(
    val min: Double,
    val max: Double,
) {
    init {
        require(max >= min) { "정규화 상한은 하한 이상이어야 합니다: min=$min, max=$max" }
    }

    /** 구간의 폭이 0이면(전 지역 동일 값) 순위를 가르지 못한다 — [DEGENERATE_VALUE] 참조. */
    val isDegenerate: Boolean get() = max == min

    /**
     * 원본 값을 0~100으로 정규화한다. 구간 밖의 값은 잘라낸다(clamp).
     *
     * [RegionScoreFactor.inverted]인 항목(혼잡도)은 **방향이 뒤집힌다** — 원본 최댓값이
     * 0점, 최솟값이 100점이다. "덜 붐빌수록 높은 점수"가 이 한 줄에만 존재한다.
     * 그 외 항목은 원본 최댓값이 100점이다(R11).
     */
    fun normalize(factor: RegionScoreFactor, rawValue: Double): RegionScore {
        val ratio = if (isDegenerate) DEGENERATE_RATIO else ((rawValue - min) / (max - min)).coerceIn(0.0, 1.0)
        val directed = if (factor.inverted) 1.0 - ratio else ratio
        return RegionScore(factor, directed * RegionScore.MAX_VALUE)
    }

    companion object {
        /**
         * 구간 폭이 0일 때 쓰는 비율. **모든 지역이 같은 값을 받으므로 순위에 영향이 없고,**
         * 중립인 0.5(=50점)를 쓴다. 0이나 100을 주면 반전 항목 여부에 따라 그 항목만
         * 총점을 통째로 끌어내리거나 끌어올린다.
         */
        private const val DEGENERATE_RATIO = 0.5

        /** 구간 폭이 0인 항목이 받는 점수. */
        const val DEGENERATE_VALUE = DEGENERATE_RATIO * RegionScore.MAX_VALUE
    }
}
