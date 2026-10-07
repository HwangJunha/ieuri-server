package org.around.ieuri.community.application.conversation.required.dto

data class ConversationMessagesSearch(
    val conversationId: Long,
    val memberId: Long,
    val first: Int,
    val after: String?,
)
