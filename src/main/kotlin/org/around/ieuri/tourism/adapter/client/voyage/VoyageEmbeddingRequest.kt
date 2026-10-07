package org.around.ieuri.tourism.adapter.client.voyage

import com.fasterxml.jackson.annotation.JsonProperty
/** `POST /v1/embeddings` 요청 본문. */
data class VoyageEmbeddingRequest(
    val input: List<String>,
    val model: String,
    @field:JsonProperty("input_type")
    val inputType: String,
)
