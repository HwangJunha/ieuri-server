package org.around.ieuri.tourism.application.recommendation.provided.dto

/** 임베딩할 번역 행 하나와 제공자에 보낼 텍스트. */
data class EmbeddingInput(
    val translationId: Long,
    /** 대상을 읽을 때의 원문 지문. 벡터와 함께 저장한다. */
    val sourceHash: String,
    val text: String,
)
