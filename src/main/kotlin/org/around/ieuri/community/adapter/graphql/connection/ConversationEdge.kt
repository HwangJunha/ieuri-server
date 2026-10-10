package org.around.ieuri.community.adapter.graphql.connection

import org.around.ieuri.community.application.conversation.provided.dto.ConversationView

data class ConversationEdge(
    val cursor: String,
    val node: ConversationView,
)
