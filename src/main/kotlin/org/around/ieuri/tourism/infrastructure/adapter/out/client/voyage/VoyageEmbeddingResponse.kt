package org.around.ieuri.tourism.infrastructure.adapter.out.client.voyage

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/** `POST /v1/embeddings` 요청 본문. */
data class VoyageEmbeddingRequest(
    val input: List<String>,
    val model: String,
    @field:JsonProperty("input_type")
    val inputType: String,
)

/** `POST /v1/embeddings` 응답. [data]의 [Item.index]가 입력 순서다 — 순서를 보장받으려면 index로 정렬한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VoyageEmbeddingResponse(
    val data: List<Item> = emptyList(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        val index: Int = 0,
        val embedding: List<Float> = emptyList(),
    )
}
