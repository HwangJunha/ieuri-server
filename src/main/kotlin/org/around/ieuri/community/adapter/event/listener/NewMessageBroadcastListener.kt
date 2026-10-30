package org.around.ieuri.community.adapter.event.listener

import org.around.ieuri.community.application.conversation.NewMessageStored
import org.around.ieuri.community.application.conversation.provided.MessageBroadcastUseCase
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 새 메시지를 커밋된 뒤에 브로커로 알린다(SPEC-0017 R14). 롤백되면 발행하지 않는다.
 * - 커밋 전에 발행하면 구독자가 그 id로 읽을 때 행이 보이지 않아 무작위로 유실된다.
 */
@Component
class NewMessageBroadcastListener(
    private val messageBroadcastUseCase: MessageBroadcastUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: NewMessageStored) {
        messageBroadcastUseCase.broadcastNewMessage(event.conversationId, event.messageId)
    }
}
