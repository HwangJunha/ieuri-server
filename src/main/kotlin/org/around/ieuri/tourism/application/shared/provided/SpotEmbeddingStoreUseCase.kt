package org.around.ieuri.tourism.application.shared.provided

import org.around.ieuri.tourism.application.shared.required.SpotEmbedding

/** 장소 임베딩의 DB 쪽 절반(SPEC-0035). 제공자 호출은 트랜잭션 밖이라 읽기와 저장을 따로 연다. */
interface SpotEmbeddingStoreUseCase {
    /** 임베딩할 번역 행과 그 텍스트, id 오름차순 [limit]건. [afterTranslationId]보다 큰 id만(키셋). */
    fun findInputs(afterTranslationId: Long, limit: Int): List<EmbeddingInput>

    /** 번역 행별 벡터를 저장한다(upsert). */
    fun save(embeddings: List<SpotEmbedding>)
}

/** 임베딩할 번역 행 하나와 제공자에 보낼 텍스트. */
data class EmbeddingInput(
    val translationId: Long,
    /** 대상을 읽을 때의 원문 지문. 벡터와 함께 저장한다. */
    val sourceHash: String,
    val text: String,
)
