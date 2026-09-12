package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.specification.TaxRefundTitleRule
import org.around.ieuri.tourism.domain.strategy.RecommendationScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** SPEC-0035 — 거리 감쇠와 면세 배율. */
class RecommendationScoreTest {

    private fun score(multiplier: Double = 0.8, patterns: List<String> = listOf("[Tax Refund Shop]")) =
        RecommendationScore(
            distanceHalfLifeMeters = 5_000.0,
            taxRefundMultiplier = multiplier,
            taxRefundTitleRule = TaxRefundTitleRule(patterns),
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
}
