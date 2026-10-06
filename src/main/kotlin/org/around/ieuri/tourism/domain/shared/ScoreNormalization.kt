package org.around.ieuri.tourism.domain.shared

/** 한 항목의 정규화 기준 구간(SPEC-0015 R11). 모집단 선택은 호출자가 한다. */
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
     * - [RegionScoreFactor.inverted]인 항목은 원본 최댓값이 0점, 그 외는 100점이다(R11).
     */
    fun normalize(factor: RegionScoreFactor, rawValue: Double): RegionScore {
        val ratio = if (isDegenerate) DEGENERATE_RATIO else ((rawValue - min) / (max - min)).coerceIn(0.0, 1.0)
        val directed = if (factor.inverted) 1.0 - ratio else ratio
        return RegionScore(factor, directed * RegionScore.MAX_VALUE)
    }

    companion object {
        /** 구간 폭이 0일 때 쓰는 비율. 전 지역이 같은 값을 받으므로 중립인 0.5를 쓴다. */
        private const val DEGENERATE_RATIO = 0.5

        /** 구간 폭이 0인 항목이 받는 점수. */
        const val DEGENERATE_VALUE = DEGENERATE_RATIO * RegionScore.MAX_VALUE
    }
}
