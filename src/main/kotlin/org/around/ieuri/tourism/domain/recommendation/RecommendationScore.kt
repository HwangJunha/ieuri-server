package org.around.ieuri.tourism.domain.recommendation

import org.around.ieuri.tourism.domain.spot.TaxRefundTitleRule

/**
 * 추천 점수(SPEC-0035). 가까울수록 높고, 취향이 있으면 섞이고, 면세 태그 장소는 배율만큼 깎인다.
 *
 * - `거리 요인 = 1 / (1 + 거리 ÷ 반감거리)` — 거리 0이면 1, 반감 거리에서 0.5.
 * - `취향 요인 = ((cos좋아요 − cos싫어요) + 1) ÷ 2` — 싫어요 평균이 없으면 cos싫어요 = 0.
 * - 취향이 있으면 `(1 − w)·거리 + w·취향`, 없으면 거리만(POL-043 — 없는 요인은 분모에서도 뺀다).
 * - 이미 반응한 장소는 배율로 올리고 내린다 — **숨기지 않는다**(SPEC-0036 D-13).
 */
class RecommendationScore(
    /** 거리 요인이 0.5가 되는 거리(m). */
    private val distanceHalfLifeMeters: Double,
    private val taxRefundMultiplier: Double,
    private val taxRefundTitleRule: TaxRefundTitleRule,
    /** 취향 요인의 비중 w(0~1). 취향이 없는 회원·후보에는 쓰이지 않는다. */
    private val tasteWeight: Double,
    /** 좋아요한 장소의 점수 배율. 1보다 크면 앞으로 당긴다. */
    private val likedMultiplier: Double = 1.0,
    /** 싫어요한 장소의 점수 배율. 1보다 작으면 뒤로 민다. 0이어도 결과에서 빠지지는 않는다. */
    private val dislikedMultiplier: Double = 1.0,
) {
    init {
        require(distanceHalfLifeMeters > 0) { "거리 반감 거리는 양수여야 합니다." }
        require(taxRefundMultiplier in 0.0..1.0) { "면세 배율은 0~1이어야 합니다." }
        require(tasteWeight in 0.0..1.0) { "취향 비중은 0~1이어야 합니다." }
        require(likedMultiplier >= 0) { "좋아요 배율은 음수일 수 없습니다." }
        require(dislikedMultiplier >= 0) { "싫어요 배율은 음수일 수 없습니다." }
    }

    fun distanceFactor(distanceMeters: Double): Double = 1.0 / (1.0 + distanceMeters / distanceHalfLifeMeters)

    fun tasteFactor(likeSimilarity: Double, dislikeSimilarity: Double?): Double = ((likeSimilarity - (dislikeSimilarity ?: 0.0) + 1.0) / 2.0).coerceIn(0.0, 1.0)

    /** 후보별 취향 요인을 후보 집단 안에서 0~1로 펼친다([TasteNormalization]). 좋아요 유사도가 없는 후보는 키가 없다. */
    fun tasteFactors(similarities: Map<Long, TasteSimilarity>): Map<Long, Double> = TasteNormalization.normalize(
        similarities.mapNotNull { (id, s) -> s.like?.let { like -> id to tasteFactor(like, s.dislike) } }.toMap(),
    )

    fun score(
        distanceMeters: Double,
        title: String,
        taste: Double? = null,
        reaction: ScoreReaction = ScoreReaction.NONE,
    ): Double {
        val distance = distanceFactor(distanceMeters)
        val base = if (taste == null) distance else (1.0 - tasteWeight) * distance + tasteWeight * taste
        val taxed = if (taxRefundTitleRule.matches(title)) base * taxRefundMultiplier else base
        return taxed * reactionMultiplier(reaction)
    }

    private fun reactionMultiplier(reaction: ScoreReaction): Double = when (reaction) {
        ScoreReaction.NONE -> 1.0
        ScoreReaction.LIKED -> likedMultiplier
        ScoreReaction.DISLIKED -> dislikedMultiplier
    }
}
