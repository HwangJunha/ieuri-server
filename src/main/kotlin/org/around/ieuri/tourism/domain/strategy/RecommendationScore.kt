package org.around.ieuri.tourism.domain.strategy

import org.around.ieuri.tourism.domain.specification.TaxRefundTitleRule

/**
 * 추천 점수(SPEC-0035). 가까울수록 높고, 면세 태그 장소는 배율만큼 깎인다. 순수 함수이며 계수는 주입된다.
 *
 * `거리 요인 = 1 / (1 + 거리 ÷ 반감거리)` — 거리 0이면 1, 반감 거리에서 0.5. 취향 요인은 S2에서 더한다.
 */
class RecommendationScore(
    /** 거리 요인이 0.5가 되는 거리(m). */
    private val distanceHalfLifeMeters: Double,
    private val taxRefundMultiplier: Double,
    private val taxRefundTitleRule: TaxRefundTitleRule,
) {
    init {
        require(distanceHalfLifeMeters > 0) { "거리 반감 거리는 양수여야 합니다." }
        require(taxRefundMultiplier in 0.0..1.0) { "면세 배율은 0~1이어야 합니다." }
    }

    fun distanceFactor(distanceMeters: Double): Double = 1.0 / (1.0 + distanceMeters / distanceHalfLifeMeters)

    fun score(distanceMeters: Double, title: String): Double {
        val base = distanceFactor(distanceMeters)
        return if (taxRefundTitleRule.matches(title)) base * taxRefundMultiplier else base
    }
}
