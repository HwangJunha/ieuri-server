package org.around.ieuri.community.adapter.graphql.connection

import org.around.ieuri.community.application.question.provided.dto.SpotAnswerView

data class SpotAnswerEdge(
    val cursor: String,
    val node: SpotAnswerView,
)
