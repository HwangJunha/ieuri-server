package org.around.ieuri.community.adapter.graphql.connection

import org.around.ieuri.community.application.question.provided.dto.SpotQuestionView

data class SpotQuestionEdge(
    val cursor: String,
    val node: SpotQuestionView,
)
