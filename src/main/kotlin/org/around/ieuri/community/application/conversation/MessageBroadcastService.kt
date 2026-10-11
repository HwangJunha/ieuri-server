package org.around.ieuri.community.application.conversation

import org.around.ieuri.community.application.conversation.provided.MessageBroadcastUseCase
import org.around.ieuri.community.application.conversation.required.ConversationEventPort
import org.springframework.stereotype.Service

/** 새 메시지 알림. DB를 읽지 않아 트랜잭션이 없다. */
@Service
class MessageBroadcastService(
    private val conversationEventPort: ConversationEventPort,
) : MessageBroadcastUseCase {

    override fun broadcastNewMessage(conversationId: Long, messageId: Long) = conversationEventPort.publishNewMessage(conversationId, messageId)
}
