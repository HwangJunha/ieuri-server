package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 장소 임베딩 저장 포트(SPEC-0035 S2). **생 SQL 어댑터가 뒤에 있다** — `vector` 타입을 JPA가 표현하지
 * 못한다. 대상 판정 한 규칙(벡터 없음·텍스트 변경·모델 불일치)이 [findTargets]의 조건이다.
 */
interface SpotEmbeddingStorePort {
    /**
     * 임베딩이 필요한 번역 행을 id 오름차순으로 [limit]건. [afterTranslationId]보다 큰 id만(키셋).
     * 벡터가 없거나, 제목·분류·주소·소개문이 바뀌었거나, [model]과 다른 모델로 만든 행이 대상이다 —
     * 상세가 나중에 적재되면 소개문이 생겨 그 행이 다시 대상이 된다.
     */
    fun findTargets(afterTranslationId: Long, limit: Int, model: String): List<EmbeddingTarget>

    /** 번역 행별 벡터를 저장한다(upsert). 텍스트 해시는 어댑터가 행에서 다시 계산해 기록한다. */
    fun saveAll(embeddings: List<SpotEmbedding>, model: String)
}

/** 임베딩할 번역 행 1건. [categoryCode]는 라벨로 바꿔 텍스트에 넣는다. [overview]는 상세가 있을 때만. */
data class EmbeddingTarget(
    val translationId: Long,
    val language: DomainLocale,
    val title: String,
    val categoryCode: String,
    val address: String?,
    val overview: String?,
)

/** 저장할 벡터 1건. 배열이라 data class가 아니다. */
class SpotEmbedding(
    val translationId: Long,
    val vector: FloatArray,
)
