package org.around.ieuri.tourism.adapter.client.voyage.mapper

import org.around.ieuri.tourism.adapter.client.voyage.VoyageEmbeddingResponse

/**
 * Voyage 응답 → 입력 순서의 벡터 목록. 응답의 `index`로 정렬한다. 개수가 [expectedCount]와 다르면
 * 실패다 — 어긋난 채 저장하면 벡터가 엉뚱한 장소에 붙는다.
 */
fun VoyageEmbeddingResponse.toVectors(expectedCount: Int): List<FloatArray> {
    val ordered = data.sortedBy { it.index }
    require(ordered.size == expectedCount && ordered.mapIndexed { i, item -> item.index == i }.all { it }) {
        "Voyage 응답 개수·순서가 입력과 다릅니다(기대 $expectedCount, 실제 ${ordered.size})."
    }
    return ordered.map { it.embedding.toFloatArray() }
}
