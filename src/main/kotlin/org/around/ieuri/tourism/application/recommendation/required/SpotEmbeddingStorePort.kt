package org.around.ieuri.tourism.application.recommendation.required

import org.around.ieuri.tourism.application.recommendation.required.dto.EmbeddingTarget
import org.around.ieuri.tourism.application.recommendation.required.dto.SpotEmbedding

/** 장소 임베딩 저장 포트(SPEC-0035 S2). */
interface SpotEmbeddingStorePort {
    /**
     * 임베딩이 필요한 번역 행을 id 오름차순으로 [limit]건. [afterTranslationId]보다 큰 id만(키셋).
     * - 벡터 없음·제목·분류·주소·소개문 변경·[model] 불일치인 행이 대상이다.
     */
    fun findTargets(afterTranslationId: Long, limit: Int, model: String): List<EmbeddingTarget>

    /** 번역 행별 벡터를 저장한다(upsert). 대상을 읽을 때 받은 원문 지문을 함께 기록한다. */
    fun saveAll(embeddings: List<SpotEmbedding>, model: String)
}
