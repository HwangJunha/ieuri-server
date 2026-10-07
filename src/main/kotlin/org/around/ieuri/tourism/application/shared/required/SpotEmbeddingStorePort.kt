package org.around.ieuri.tourism.application.shared.required

import org.around.ieuri.tourism.domain.shared.DomainLocale

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

/**
 * 임베딩할 번역 행 1건. [categoryCode]는 라벨로 바꿔 텍스트에 넣는다. [overview]는 상세가 있을 때만.
 * - [sourceHash]는 제목·분류·주소·소개문의 지문이다. 저장한 지문과 달라지면 다시 대상이 된다.
 */
data class EmbeddingTarget(
    val translationId: Long,
    val sourceHash: String,
    val language: DomainLocale,
    val title: String,
    val categoryCode: String,
    val address: String?,
    val overview: String?,
)

/** 저장할 벡터 1건. 배열이라 data class가 아니다. */
class SpotEmbedding(
    val translationId: Long,
    val sourceHash: String,
    val vector: FloatArray,
)
