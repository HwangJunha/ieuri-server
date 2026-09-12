package org.around.ieuri.tourism.infrastructure

import org.around.ieuri.tourism.infrastructure.config.RecommendationProperties
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/** SPEC-0035 R25 — 설정 없이도 §9 표의 출발값으로 동작하고, 설정으로 덮어쓸 수 있다. */
class RecommendationPropertiesTest {

    @Test
    fun `기본값은 스펙 §9 표와 같다 - AC-25_1`() {
        val p = RecommendationProperties()
        assertEquals(0.4, p.score.weights.distance)
        assertEquals(0.25, p.score.weights.popularity)
        assertEquals(0.15, p.score.weights.activity)
        assertEquals(0.2, p.score.weights.affinity)
        assertEquals(0.0, p.score.weights.trend)
        assertEquals(5_000.0, p.score.distanceHalfLifeMeters)
        assertEquals(100L, p.score.popularityCap)
        assertEquals(50L, p.score.activityCap)
        assertEquals(0.8, p.score.taxRefundMultiplier)
        assertEquals(listOf("[Tax Refund Shop]"), p.score.taxRefundTitlePatterns)
        assertEquals(1.0, p.dpp.alpha)
        assertEquals(0.5, p.dpp.sigma)
        assertEquals(12, p.dpp.window)
        assertEquals(300, p.dpp.candidateCap)
        assertEquals(200, p.dpp.batchSize)
        assertEquals(0.3, p.dpp.sameCategoryDistance)
        assertEquals(1.0, p.dpp.differentCategoryDistance)
        assertEquals(Duration.ofHours(24), p.cursor.maxAge)
        assertEquals(Duration.ofMinutes(30), p.cache.ttl)
    }

    @Test
    fun `설정으로 덮어쓰면 그 값이다 - AC-25_2`() {
        val p = RecommendationProperties(
            dpp = RecommendationProperties.Dpp(alpha = 2.0, window = 8),
            cursor = RecommendationProperties.Cursor(maxAge = Duration.ofHours(1)),
        )
        assertEquals(2.0, p.dpp.alpha)
        assertEquals(8, p.dpp.window)
        assertEquals(Duration.ofHours(1), p.cursor.maxAge)
        assertEquals(0.5, p.dpp.sigma)
    }
}
