package org.around.ieuri.community.application.conversation.required.dto

data class ConversationsSearch(
    val memberId: Long,
    val first: Int,
    val after: String?,
)
