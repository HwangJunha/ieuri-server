package org.around.ieuri.community.adapter.graphql.connection

import org.around.ieuri.community.application.conversation.provided.dto.MessageView

data class MessageEdge(
    val cursor: String,
    val node: MessageView,
)
