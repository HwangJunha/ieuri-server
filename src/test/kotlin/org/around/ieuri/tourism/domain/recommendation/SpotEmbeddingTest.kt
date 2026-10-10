package org.around.ieuri.tourism.domain.recommendation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 임베딩은 모델 차원과 같은 벡터로만 만들어진다. */
class SpotEmbeddingTest {

    @Test
    fun `차원이 같으면 번역 id·모델·지문·벡터를 그대로 담는다`() {
        val embedding = SpotEmbedding.of(translationId = 11, model = "voyage-4", dimension = 3, sourceHash = "hash-11", vector = floatArrayOf(1f, 0f, 0f))

        assertEquals(11L, embedding.translationId)
        assertEquals("voyage-4", embedding.model)
        assertEquals("hash-11", embedding.sourceHash)
        assertEquals(listOf(1f, 0f, 0f), embedding.vector.toList())
    }

    @Test
    fun `차원이 다르면 만들지 않는다`() {
        assertFailsWith<EmbeddingDimensionMismatchException> {
            SpotEmbedding.of(translationId = 12, model = "voyage-4", dimension = 3, sourceHash = "hash-12", vector = floatArrayOf(1f, 0f))
        }
    }
}
