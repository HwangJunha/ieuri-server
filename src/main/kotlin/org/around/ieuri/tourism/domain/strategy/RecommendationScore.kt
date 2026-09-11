package org.around.ieuri.tourism.domain.strategy

import org.around.ieuri.tourism.domain.specification.TaxRefundTitleRule
import org.around.ieuri.tourism.domain.vo.ScoreFactors
import org.around.ieuri.tourism.domain.vo.ScoreWeights
import kotlin.math.ln
import kotlin.math.max

/**
 * 추천 점수(SPEC-0035 R6~R12). 요인을 0~1로 정규화하고 가중합을 낸다. 순수 함수이며 계수는 전부 주입된다.
 *
 * - 없는 요인은 분모에서도 빠진다(R6). 요인이 하나도 없으면 점수는 **null**이며 0이 아니다.
 * - 면세 태그 장소는 가중합에 [taxRefundMultiplier]를 곱한다(R11).
 */
class RecommendationScore(
    private val weights: ScoreWeights,
    /** 거리 요인이 0.5가 되는 거리(m). */
    private val distanceHalfLifeMeters: Double,
    /** 인기 요인이 1이 되는 순좋아요 수. */
    private val popularityCap: Long,
    /** 활동 요인이 1이 되는 글·질문·답변 합. */
    private val activityCap: Long,
    private val taxRefundMultiplier: Double,
    private val taxRefundTitleRule: TaxRefundTitleRule,
) {
    init {
        require(distanceHalfLifeMeters > 0) { "거리 반감 거리는 양수여야 합니다." }
        require(popularityCap > 0 && activityCap > 0) { "요인 상한은 양수여야 합니다." }
        require(taxRefundMultiplier in 0.0..1.0) { "면세 배율은 0~1이어야 합니다." }
    }

    /** `1 / (1 + 거리 ÷ 반감거리)`. 거리 0이면 1, 반감 거리에서 0.5(R7). */
    fun distanceFactor(distanceMeters: Double): Double = 1.0 / (1.0 + distanceMeters / distanceHalfLifeMeters)

    /** `log(1 + max(좋아요 − 싫어요, 0)) ÷ log(1 + 상한)`을 0~1로 자른다(R8). */
    fun popularityFactor(likes: Long, dislikes: Long): Double = logRatio(max(likes - dislikes, 0L), popularityCap)

    /** `log(1 + 글 + 질문 + 답변) ÷ log(1 + 상한)`을 0~1로 자른다(R9). */
    fun activityFactor(posts: Long, questions: Long, answers: Long): Double =
        logRatio(posts + questions + answers, activityCap)

    /** 코사인 유사도(−1~1)를 0~1로 옮긴다(R10). */
    fun affinityFactor(cosineSimilarity: Double): Double = ((cosineSimilarity + 1.0) / 2.0).coerceIn(0.0, 1.0)

    /**
     * 가중합 ÷ 가중치 합. 값이 있는 요인만 센다. **요인이 없거나 가중치 합이 0이면 null** —
     * 그 장소는 순서상 맨 뒤로 간다(AC-6.2).
     */
    fun score(factors: ScoreFactors, title: String): Double? {
        val present = factors.present(weights)
        val denominator = present.sumOf { (_, weight) -> weight }
        if (present.isEmpty() || denominator <= 0.0) return null
        val weighted = present.sumOf { (value, weight) -> value * weight } / denominator
        return if (taxRefundTitleRule.matches(title)) weighted * taxRefundMultiplier else weighted
    }

    private fun logRatio(value: Long, cap: Long): Double =
        (ln(1.0 + value) / ln(1.0 + cap)).coerceIn(0.0, 1.0)
}
