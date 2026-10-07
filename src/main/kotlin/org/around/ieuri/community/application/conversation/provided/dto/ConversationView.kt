package org.around.ieuri.community.application.conversation.provided.dto

import java.time.Instant

/** 대화 목록 항목. 빈 대화면 [lastMessage]가 null이다. */
data class ConversationView(
    val id: Long,
    val counterpartId: Long,
    val lastMessage: MessageView?,
    val unreadCount: Int,
    val updatedAt: Instant,
)
