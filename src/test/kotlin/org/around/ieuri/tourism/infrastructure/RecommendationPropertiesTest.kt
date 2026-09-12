package org.around.ieuri.tourism.infrastructure

import org.around.ieuri.tourism.infrastructure.config.RecommendationProperties
import kotlin.test.Test
import kotlin.test.assertEquals

/** SPEC-0035 §9 — 설정 없이도 출발값으로 동작하고, 설정으로 덮어쓸 수 있다. */
class RecommendationPropertiesTest {

    @Test
    fun `기본값은 출발값이다`() {
        val p = RecommendationProperties()
        assertEquals(5_000.0, p.distanceHalfLifeMeters)
        assertEquals(0.8, p.taxRefundMultiplier)
        assertEquals(listOf("[Tax Refund Shop]"), p.taxRefundTitlePatterns)
        assertEquals(0.1, p.categoryRepeatPenalty)
        assertEquals(300, p.candidateCap)
        assertEquals(0.5, p.tasteWeight)
        assertEquals(5, p.tasteMinReactions)
        assertEquals("voyage-4", p.embedding.model)
        assertEquals(1024, p.embedding.dimension)
        assertEquals(128, p.embedding.batchSize)
        assertEquals(3, p.embedding.retryCount)
    }

    @Test
    fun `설정으로 덮어쓰면 그 값이다`() {
        val p = RecommendationProperties(categoryRepeatPenalty = 0.3, candidateCap = 50)
        assertEquals(0.3, p.categoryRepeatPenalty)
        assertEquals(50, p.candidateCap)
        assertEquals(0.8, p.taxRefundMultiplier)
    }
}
