package org.around.ieuri.tourism.adapter.client.voyage.mapper

import org.around.ieuri.tourism.adapter.client.voyage.VoyageEmbeddingResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Voyage 응답 → 입력 순서의 벡터. index로 정렬하고 개수가 다르면 실패한다. */
class VoyageEmbeddingMapperTest {

    @Test
    fun `index 순서로 정렬해 입력 순서를 복원한다`() {
        val response = VoyageEmbeddingResponse(
            data = listOf(
                VoyageEmbeddingResponse.Item(index = 1, embedding = listOf(0.5f, 0.6f)),
                VoyageEmbeddingResponse.Item(index = 0, embedding = listOf(0.1f, 0.2f)),
            ),
        )
        val vectors = response.toVectors(expectedCount = 2)
        assertEquals(listOf(0.1f, 0.2f), vectors[0].toList())
        assertEquals(listOf(0.5f, 0.6f), vectors[1].toList())
    }

    @Test
    fun `개수가 입력과 다르거나 index가 빠지면 실패한다`() {
        assertFailsWith<IllegalArgumentException> {
            VoyageEmbeddingResponse(data = listOf(VoyageEmbeddingResponse.Item(0, listOf(0.1f)))).toVectors(expectedCount = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            VoyageEmbeddingResponse(
                data = listOf(VoyageEmbeddingResponse.Item(0, listOf(0.1f)), VoyageEmbeddingResponse.Item(2, listOf(0.2f))),
            ).toVectors(expectedCount = 2)
        }
    }
}
