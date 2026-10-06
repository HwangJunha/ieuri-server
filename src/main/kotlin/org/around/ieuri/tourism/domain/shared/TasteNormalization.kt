package org.around.ieuri.tourism.domain.shared

/**
 * 취향 요인을 후보 집단 안에서 0~1로 펼친다(SPEC-0036 후속, min-max).
 * - 행을 버리지 않는다 — 입력의 모든 키가 출력에 있다.
 * - 집단의 폭이 좁아도 0~1로 증폭된다(가드 없음).
 */
object TasteNormalization {
    /**
     * [factors]를 최소 0.0 · 최대 1.0이 되도록 선형 변환한다.
     * - 값이 하나뿐이거나 전부 같으면(폭 0) 모두 중립값 0.5다.
     */
    fun normalize(factors: Map<Long, Double>): Map<Long, Double> {
        if (factors.isEmpty()) return factors
        val min = factors.values.min()
        val max = factors.values.max()
        val span = max - min
        if (span <= 0.0) return factors.mapValues { NEUTRAL }
        return factors.mapValues { (_, value) -> (value - min) / span }
    }

    /** 펼칠 수 없을 때의 값. */
    private const val NEUTRAL = 0.5
}
