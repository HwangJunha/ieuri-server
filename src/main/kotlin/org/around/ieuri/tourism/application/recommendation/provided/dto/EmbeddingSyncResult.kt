package org.around.ieuri.tourism.application.recommendation.provided.dto

/** [processed]가 0이면 소진이다. [lastTranslationId]는 다음 호출의 커서. */
data class EmbeddingSyncResult(
    val processed: Int,
    val embedded: Int,
    val failed: Int,
    val lastTranslationId: Long,
)
