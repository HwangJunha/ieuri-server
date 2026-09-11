package org.around.ieuri.tourism.domain.vo

/**
 * 추천 점수의 요인(SPEC-0035 R6). 각 값은 0~1로 정규화된 것이고, **null은 "없음"이지 0이 아니다** —
 * 없는 요인은 가중합과 분모 양쪽에서 빠진다(POL-043).
 */
data class ScoreFactors(
    val distance: Double? = null,
    val popularity: Double? = null,
    val activity: Double? = null,
    val affinity: Double? = null,
    val trend: Double? = null,
) {
    /** 값이 있는 요인만 (값, 가중치) 쌍으로. */
    fun present(weights: ScoreWeights): List<Pair<Double, Double>> = listOfNotNull(
        distance?.let { it to weights.distance },
        popularity?.let { it to weights.popularity },
        activity?.let { it to weights.activity },
        affinity?.let { it to weights.affinity },
        trend?.let { it to weights.trend },
    )
}

/** 요인별 가중치(SPEC-0035 §9). 전부 설정에서 오며 음수는 허용하지 않는다. */
data class ScoreWeights(
    val distance: Double,
    val popularity: Double,
    val activity: Double,
    val affinity: Double,
    val trend: Double,
) {
    init {
        require(listOf(distance, popularity, activity, affinity, trend).all { it >= 0.0 }) {
            "점수 가중치는 음수일 수 없습니다."
        }
    }
}
