package org.around.ieuri.community.application.conversation.provided.dto

data class NotifyTypingCommand(val conversationId: Long) {
    init {
        require(conversationId > 0) { "conversationId는 양수여야 합니다." }
    }
}
