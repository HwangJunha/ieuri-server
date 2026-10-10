package org.around.ieuri.community.application.question.required.dto

import org.around.ieuri.community.application.shared.required.dto.ViewerContext

data class SpotAnswersSearch(
    val questionId: Long,
    val first: Int,
    val after: String?,
    val viewer: ViewerContext,
)
