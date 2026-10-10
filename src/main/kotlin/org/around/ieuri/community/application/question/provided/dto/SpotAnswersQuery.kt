package org.around.ieuri.community.application.question.provided.dto

data class SpotAnswersQuery(
    val questionId: Long,
    val first: Int,
    val after: String?,
)
