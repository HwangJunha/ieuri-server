package org.around.ieuri.tourism.domain.recommendation

/**
 * 카탈로그 번역 하나의 임베딩(SPEC-0035 S2). 식별자는 [translationId] — 번역마다 하나이고, 모델이 바뀌면 덮어쓴다.
 * - [sourceHash]는 벡터를 만든 원문의 지문이다. 원문이 바뀌면 다시 만들 대상이 된다.
 * - 배열을 품어 data class가 아니다.
 */
class SpotEmbedding(
    val translationId: Long,
    val model: String,
    val sourceHash: String,
    val vector: FloatArray,
)
