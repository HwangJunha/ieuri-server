package org.around.ieuri.tourism.domain.recommendation.spec

import org.around.ieuri.tourism.domain.recommendation.ReactionHistory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SPEC-0035 — 취향 최소 반응 수. 좋아요와 싫어요를 합쳐 센다. */
class TasteThresholdTest {

    private val threshold = TasteThreshold(minReactions = 3)

    @Test
    fun `좋아요와 싫어요의 합이 최소와 같으면 성립한다`() {
        assertTrue(threshold.isSatisfiedBy(ReactionHistory(liked = setOf(1L, 2L), disliked = setOf(3L))))
    }

    @Test
    fun `합이 최소보다 하나 적으면 성립하지 않는다`() {
        assertFalse(threshold.isSatisfiedBy(ReactionHistory(liked = setOf(1L), disliked = setOf(2L))))
    }

    @Test
    fun `최소 반응 수는 1 이상이다`() {
        assertFailsWith<IllegalArgumentException> { TasteThreshold(minReactions = 0) }
    }
}
