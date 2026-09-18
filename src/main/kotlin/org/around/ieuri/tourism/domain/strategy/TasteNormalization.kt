package org.around.ieuri.tourism.domain.strategy

/**
 * 취향 요인을 후보 집단 안에서 0~1로 펼친다(SPEC-0036 후속).
 *
 * **왜 필요한가.** 취향 요인의 원값은 `((cos좋아요 − cos싫어요) + 1) / 2`인데, 임베딩 코사인이
 * 0.7~0.95 구간에 몰려 있어 두 코사인의 차가 작다. 그래서 원값이 0.5 근처로 압축되고, 실측에서
 * 후보 300건의 폭이 0.08(표준편차 0.0145)에 그쳤다 — 거리 요인의 격차(0.17)에 묻혀 취향이
 * 순위를 가르지 못했다. 좋아요를 한 번도 누르지 않은 카테고리가 계속 1위로 나오던 원인이다.
 *
 * min-max로 펼치면 같은 계수(취향 비중 0.5)로도 취향이 거리와 겨룰 수 있게 된다. 실측(가산·
 * 강남역·명동, 좋아요 46·싫어요 123인 회원): 상위 20건의 SHOPPING이 20→0 · 13→0 · 5→0으로 바뀌고
 * 평균 거리는 여전히 451m~2.7km에 머물렀다.
 *
 * **순서만 바꾸고 행을 버리지 않는다.** 입력의 모든 키가 출력에 있다.
 *
 * 주의: 집단의 폭이 좁으면 미세한 차이가 그대로 0~1로 증폭된다 — 같은 건물 매장들처럼 후보가
 * 사실상 균질한 경우(실측 폭 0.006) 거리 차이를 뒤집을 수 있다. 그 구간까지 막지 않는 것은
 * 의도된 선택이다(가드를 두지 않기로 함).
 */
object TasteNormalization {
    /**
     * [factors]를 최소 0.0 · 최대 1.0이 되도록 선형 변환한다.
     *
     * 값이 하나뿐이거나 전부 같으면(폭 0) 비교할 대상이 없다는 뜻이므로 **중립값 0.5**를 준다 —
     * 0을 주면 취향이 있는 회원이 취향 없는 회원보다 불리해지고, 1을 주면 그 반대가 된다.
     */
    fun normalize(factors: Map<Long, Double>): Map<Long, Double> {
        if (factors.isEmpty()) return factors
        val min = factors.values.min()
        val max = factors.values.max()
        val span = max - min
        if (span <= 0.0) return factors.mapValues { NEUTRAL }
        return factors.mapValues { (_, value) -> (value - min) / span }
    }

    /** 펼칠 수 없을 때의 값. 거리만 쓰는 것과 같은 효과를 내도록 중간을 준다. */
    private const val NEUTRAL = 0.5
}
