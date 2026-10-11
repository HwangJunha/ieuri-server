package org.around.ieuri.community.application.conversation.provided.dto

/** 메시지 1건이 어느 대화에 있고 누가 보냈는가. 본문은 담지 않는다. */
data class MessageOwnerView(
    val messageId: Long,
    val conversationId: Long,
    val senderId: Long,
)
