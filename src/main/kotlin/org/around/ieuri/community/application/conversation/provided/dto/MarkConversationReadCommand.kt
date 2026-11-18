package org.around.ieuri.community.application.conversation.provided.dto

/** 대화 읽음 처리. [upToMessageId]가 null이면 마지막 메시지까지 읽은 것으로 본다(모두 읽음). */
data class MarkConversationReadCommand(
    val conversationId: Long,
    val upToMessageId: Long? = null,
) {
    init {
        require(conversationId > 0) { "conversationId는 양수여야 합니다." }
        require(upToMessageId == null || upToMessageId > 0) { "upToMessageId는 양수여야 합니다." }
    }
}
