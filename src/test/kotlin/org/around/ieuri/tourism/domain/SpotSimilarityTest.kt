package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.strategy.SimilarityInput
import org.around.ieuri.tourism.domain.strategy.SpotSimilarity
import org.around.ieuri.tourism.domain.vo.SpotCategory
import kotlin.test.Test
import kotlin.test.assertEquals

/** SPEC-0035 R14·R20 — 벡터 거리와 카테고리 대체 거리. */
class SpotSimilarityTest {

    private val similarity = SpotSimilarity(sameCategoryDistance = 0.3, differentCategoryDistance = 1.0)
    private val shopping = SpotCategory("SHOPPING")
    private val restaurant = SpotCategory("RESTAURANT")

    @Test
    fun `한쪽에 벡터가 없고 카테고리가 같으면 같은 카테고리 거리다 - AC-14_1`() {
        val a = SimilarityInput(shopping, floatArrayOf(1f, 0f))
        val b = SimilarityInput(shopping, null)
        assertEquals(0.3, similarity.distance(a, b), 1e-9)
    }

    @Test
    fun `카테고리가 다르면 다른 카테고리 거리다 - AC-14_2`() {
        assertEquals(1.0, similarity.distance(SimilarityInput(shopping), SimilarityInput(restaurant)), 1e-9)
    }

    @Test
    fun `둘 다 벡터가 있으면 코사인 거리를 쓴다 - AC-14_3`() {
        val a = SimilarityInput(shopping, floatArrayOf(1f, 0f))
        val same = SimilarityInput(restaurant, floatArrayOf(2f, 0f))
        val orthogonal = SimilarityInput(shopping, floatArrayOf(0f, 3f))
        assertEquals(0.0, similarity.distance(a, same), 1e-6)
        assertEquals(1.0, similarity.distance(a, orthogonal), 1e-6)
    }

    @Test
    fun `차원이 다른 벡터는 없는 것으로 취급한다 - AC-20_2`() {
        val a = SimilarityInput(shopping, floatArrayOf(1f, 0f))
        val b = SimilarityInput(shopping, floatArrayOf(1f, 0f, 0f))
        assertEquals(0.3, similarity.distance(a, b), 1e-9)
    }
}
