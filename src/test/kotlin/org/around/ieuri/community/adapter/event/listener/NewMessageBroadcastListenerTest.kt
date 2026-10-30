package org.around.ieuri.community.adapter.event.listener

import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.conversation.NewMessageStored
import org.around.ieuri.community.application.conversation.provided.MessageBroadcastUseCase
import org.junit.jupiter.api.Test
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import kotlin.test.assertEquals

/**
 * 커밋 이후 발행(SPEC-0017 R14). 페이즈를 리플렉션으로 검증한다.
 * - AFTER_COMMIT이 아니면 구독자가 아직 커밋되지 않은 행을 읽어 이벤트가 무작위로 유실된다.
 */
class NewMessageBroadcastListenerTest {

    private val messageBroadcast = mockk<MessageBroadcastUseCase>(relaxed = true)
    private val listener = NewMessageBroadcastListener(messageBroadcast)

    @Test
    fun `저장 이벤트를 받으면 브로드캐스트 유스케이스를 부른다`() {
        listener.on(NewMessageStored(conversationId = 7L, messageId = 500L))

        verify { messageBroadcast.broadcastNewMessage(7L, 500L) }
    }

    /** 커밋 이후(AFTER_COMMIT)에 발행한다. */
    @Test
    fun `커밋 이후 페이즈로 등록된다`() {
        val method = NewMessageBroadcastListener::class.java
            .getDeclaredMethod("on", NewMessageStored::class.java)
        val annotation = method.getAnnotation(TransactionalEventListener::class.java)

        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase)
    }
}
