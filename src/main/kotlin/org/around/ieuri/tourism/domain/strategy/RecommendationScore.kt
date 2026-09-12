package org.around.ieuri.tourism.domain.strategy

import org.around.ieuri.tourism.domain.specification.TaxRefundTitleRule

/**
 * 추천 점수(SPEC-0035). 가까울수록 높고, 취향이 있으면 섞이고, 면세 태그 장소는 배율만큼 깎인다.
 * 순수 함수이며 계수는 주입된다.
 *
 * - `거리 요인 = 1 / (1 + 거리 ÷ 반감거리)` — 거리 0이면 1, 반감 거리에서 0.5.
 * - `취향 요인 = ((cos좋아요 − cos싫어요) + 1) ÷ 2` — 싫어요 평균이 없으면 cos싫어요 = 0.
 * - 취향이 있으면 `(1 − w)·거리 + w·취향`, 없으면 거리만(POL-043 — 없는 요인은 분모에서도 뺀다).
 */
class RecommendationScore(
    /** 거리 요인이 0.5가 되는 거리(m). */
    private val distanceHalfLifeMeters: Double,
    private val taxRefundMultiplier: Double,
    private val taxRefundTitleRule: TaxRefundTitleRule,
    /** 취향 요인의 비중 w(0~1). 취향이 없는 회원·후보에는 쓰이지 않는다. */
    private val tasteWeight: Double,
) {
    init {
        require(distanceHalfLifeMeters > 0) { "거리 반감 거리는 양수여야 합니다." }
        require(taxRefundMultiplier in 0.0..1.0) { "면세 배율은 0~1이어야 합니다." }
        require(tasteWeight in 0.0..1.0) { "취향 비중은 0~1이어야 합니다." }
    }

    fun distanceFactor(distanceMeters: Double): Double = 1.0 / (1.0 + distanceMeters / distanceHalfLifeMeters)

    fun tasteFactor(likeSimilarity: Double, dislikeSimilarity: Double?): Double =
        ((likeSimilarity - (dislikeSimilarity ?: 0.0) + 1.0) / 2.0).coerceIn(0.0, 1.0)

    fun score(distanceMeters: Double, title: String, taste: Double? = null): Double {
        val distance = distanceFactor(distanceMeters)
        val base = if (taste == null) distance else (1.0 - tasteWeight) * distance + tasteWeight * taste
        return if (taxRefundTitleRule.matches(title)) base * taxRefundMultiplier else base
    }
}
