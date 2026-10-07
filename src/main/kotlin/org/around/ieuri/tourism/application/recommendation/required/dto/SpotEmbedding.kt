package org.around.ieuri.tourism.application.recommendation.required.dto

/** 저장할 벡터 1건. 배열이라 data class가 아니다. */
class SpotEmbedding(
    val translationId: Long,
    val sourceHash: String,
    val vector: FloatArray,
)
