package org.around.ieuri.tourism.domain.recommendation

import org.around.ieuri.tourism.domain.spot.TaxRefundTitleRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** SPEC-0035 — 거리 감쇠·취향 섞기·면세 배율. */
class RecommendationScoreTest {

    private fun score(
        multiplier: Double = 0.8,
        patterns: List<String> = listOf("[Tax Refund Shop]"),
        tasteWeight: Double = 0.5,
    ) = RecommendationScore(
        distanceHalfLifeMeters = 5_000.0,
        taxRefundMultiplier = multiplier,
        taxRefundTitleRule = TaxRefundTitleRule(patterns),
        tasteWeight = tasteWeight,
    )

    @Test
    fun `거리 요인은 0에서 1, 반감 거리에서 0_5, 세 배에서 0_25다`() {
        assertEquals(1.0, score().distanceFactor(0.0), 1e-9)
        assertEquals(0.5, score().distanceFactor(5_000.0), 1e-9)
        assertEquals(0.25, score().distanceFactor(15_000.0), 1e-9)
    }

    @Test
    fun `가까운 쪽 점수가 높다`() {
        assertTrue(score().score(1_000.0, "a") > score().score(9_000.0, "b"))
    }

    @Test
    fun `태그 장소는 배율만큼 깎인다`() {
        val plain = score().score(2_000.0, "가산 아울렛")
        val tagged = score().score(2_000.0, "[Tax Refund Shop] 가산 아울렛")
        assertEquals(plain * 0.8, tagged, 1e-9)
    }

    @Test
    fun `배율 1이면 감점이 없다`() {
        assertEquals(score(multiplier = 1.0).score(2_000.0, "a"), score(multiplier = 1.0).score(2_000.0, "[Tax Refund Shop] a"), 1e-9)
    }

    @Test
    fun `패턴이 없으면 태그가 있어도 감점하지 않는다`() {
        assertEquals(score().score(2_000.0, "a"), score(patterns = emptyList()).score(2_000.0, "[Tax Refund Shop] a"), 1e-9)
    }

    @Test
    fun `취향 요인은 좋아요 코사인에서 싫어요 코사인을 뺀 값을 0~1로 옮긴다`() {
        assertEquals(1.0, score().tasteFactor(likeSimilarity = 1.0, dislikeSimilarity = 0.0), 1e-9)
        assertEquals(0.5, score().tasteFactor(likeSimilarity = 0.0, dislikeSimilarity = null), 1e-9)
        assertEquals(0.0, score().tasteFactor(likeSimilarity = -0.5, dislikeSimilarity = 0.5), 1e-9)
        assertEquals(0.75, score().tasteFactor(likeSimilarity = 0.8, dislikeSimilarity = 0.3), 1e-9)
    }

    @Test
    fun `취향이 있으면 비중만큼 섞이고 없으면 거리만이다`() {
        val distance = score().distanceFactor(5_000.0) // 0.5
        assertEquals(distance, score().score(5_000.0, "a", taste = null), 1e-9)
        assertEquals(0.5 * distance + 0.5 * 1.0, score().score(5_000.0, "a", taste = 1.0), 1e-9)
        assertEquals(0.8 * distance + 0.2 * 0.0, score(tasteWeight = 0.2).score(5_000.0, "a", taste = 0.0), 1e-9)
    }

    @Test
    fun `취향이 섞인 뒤에 면세 배율이 곱해진다`() {
        val expected = (0.5 * score().distanceFactor(1_000.0) + 0.5 * 0.9) * 0.8
        assertEquals(expected, score().score(1_000.0, "[Tax Refund Shop] a", taste = 0.9), 1e-9)
    }

    @Test
    fun `취향 요인은 후보 집단 안에서 0~1로 펼치고, 좋아요 유사도가 없는 후보는 뺀다`() {
        val factors = score().tasteFactors(
            mapOf(
                1L to TasteSimilarity(like = 0.8, dislike = 0.3),
                2L to TasteSimilarity(like = 0.0, dislike = null),
                3L to TasteSimilarity(like = 0.4, dislike = 0.0),
                4L to TasteSimilarity(like = null, dislike = 0.2),
            ),
        )

        assertEquals(setOf(1L, 2L, 3L), factors.keys)
        assertEquals(1.0, factors.getValue(1L), 1e-9)
        assertEquals(0.0, factors.getValue(2L), 1e-9)
        // 요인 0.75 / 0.5 / 0.7 → (0.7 − 0.5) ÷ (0.75 − 0.5)
        assertEquals(0.8, factors.getValue(3L), 1e-9)
    }
}
