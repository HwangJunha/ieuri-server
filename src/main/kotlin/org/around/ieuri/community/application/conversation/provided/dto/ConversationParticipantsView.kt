package org.around.ieuri.community.application.conversation.provided.dto

/** 대화의 두 참여자. */
data class ConversationParticipantsView(
    val conversationId: Long,
    val memberLow: Long,
    val memberHigh: Long,
)
