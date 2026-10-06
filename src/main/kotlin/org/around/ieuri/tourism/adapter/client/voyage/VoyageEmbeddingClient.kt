package org.around.ieuri.tourism.adapter.client.voyage

import org.around.ieuri.tourism.adapter.client.voyage.mapper.toVectors
import org.around.ieuri.tourism.adapter.config.VoyageProperties
import org.around.ieuri.tourism.application.shared.required.TextEmbeddingPort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Voyage 임베딩 클라이언트(SPEC-0035). `POST /v1/embeddings`. 키는 환경변수로만 오고 로그·응답에 노출하지 않는다.
 * - 전송·응답 실패는 그대로 예외로 올린다 — 재시도는 서비스가 한다.
 */
@Component
class VoyageEmbeddingClient(
    private val properties: VoyageProperties,
    private val voyageRestClient: RestClient,
) : TextEmbeddingPort {

    override fun embed(texts: List<String>, model: String): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val response = voyageRestClient.post()
            .uri(properties.baseUrl + EMBEDDINGS_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
            .contentType(MediaType.APPLICATION_JSON)
            .body(VoyageEmbeddingRequest(input = texts, model = model, inputType = INPUT_TYPE_DOCUMENT))
            .retrieve()
            .body(VoyageEmbeddingResponse::class.java)
            ?: throw IllegalStateException("Voyage 응답이 비어 있습니다.")
        return response.toVectors(expectedCount = texts.size)
    }

    private companion object {
        const val EMBEDDINGS_PATH = "/v1/embeddings"

        /** 검색 질의가 아니라 저장할 문서다. */
        const val INPUT_TYPE_DOCUMENT = "document"
    }
}
