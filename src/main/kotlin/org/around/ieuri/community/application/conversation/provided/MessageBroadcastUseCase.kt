package org.around.ieuri.community.application.conversation.provided

/** 저장된 새 메시지를 구독자에게 알린다(SPEC-0017 R14). */
interface MessageBroadcastUseCase {
    /** 커밋된 뒤에 부른다 — 커밋 전에 알리면 구독자가 그 id로 읽을 때 행이 보이지 않는다. */
    fun broadcastNewMessage(conversationId: Long, messageId: Long)
}
