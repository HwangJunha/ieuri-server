package org.around.ieuri.community.infrastructure.adapter.out.event

import org.around.ieuri.community.application.event.NewMessageStored
import org.around.ieuri.community.application.port.out.event.ConversationEventPort
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 새 메시지를 **커밋된 뒤에** 브로커로 알린다(SPEC-0017 R14).
 *
 * 트랜잭션 안에서 발행하면 구독자가 그 id로 DB를 읽는 시점에 아직 커밋 전이라 행이 보이지
 * 않는다. 조회는 별도 커넥션에서 돌기 때문이다 — 커밋과 조회의 경합이라 유실이 무작위로
 * 나타난다(실측: 커밋 2ms 전에 조회가 닿아 `찾음=false`).
 *
 * 롤백되면 발행 자체가 일어나지 않는다. 저장되지 않은 메시지를 알릴 이유가 없다.
 */
@Component
class NewMessageBroadcastListener(
    private val conversationEventPort: ConversationEventPort,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: NewMessageStored) {
        conversationEventPort.publishNewMessage(event.conversationId, event.messageId)
    }
}
