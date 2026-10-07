package org.around.ieuri.tourism.application.recommendation.required.dto

/** 후보 하나의 유사도. [dislike]는 싫어요 장소가 없거나 벡터가 없으면 null. */
data class TasteSimilarity(
    val like: Double?,
    val dislike: Double?,
)
