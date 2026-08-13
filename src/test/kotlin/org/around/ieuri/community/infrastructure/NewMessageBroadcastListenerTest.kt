package org.around.ieuri.community.infrastructure

import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.event.NewMessageStored
import org.around.ieuri.community.application.port.out.event.ConversationEventPort
import org.around.ieuri.community.infrastructure.adapter.out.event.NewMessageBroadcastListener
import org.junit.jupiter.api.Test
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import kotlin.test.assertEquals

/**
 * 커밋 이후 발행(SPEC-0017 R14).
 *
 * **AFTER_COMMIT이 이 리스너의 존재 이유 전부다.** 커밋 전에 발행하면 구독자가 그 id로 DB를
 * 읽는 시점에 행이 아직 보이지 않아 이벤트가 조용히 버려진다 — 커밋과 조회의 경합이라
 * 유실이 무작위로 나타난다(실측: 커밋 2ms 전에 조회가 닿아 `찾음=false`).
 *
 * 그래서 페이즈를 리플렉션으로 검증한다. 누군가 AFTER_COMMIT을 지우거나 기본값
 * (`@EventListener`)으로 바꾸면 증상이 "가끔 메시지가 안 온다"로만 나타나 추적이 매우 어렵다.
 */
class NewMessageBroadcastListenerTest {

    private val conversationEventPort = mockk<ConversationEventPort>(relaxed = true)
    private val listener = NewMessageBroadcastListener(conversationEventPort)

    @Test
    fun `저장 이벤트를 받으면 브로커로 발행한다`() {
        listener.on(NewMessageStored(conversationId = 7L, messageId = 500L))

        verify { conversationEventPort.publishNewMessage(7L, 500L) }
    }

    /** **커밋 이후여야 한다.** 이 페이즈가 바뀌면 유실이 무작위로 돌아온다. */
    @Test
    fun `커밋 이후 페이즈로 등록된다`() {
        val method = NewMessageBroadcastListener::class.java
            .getDeclaredMethod("on", NewMessageStored::class.java)
        val annotation = method.getAnnotation(TransactionalEventListener::class.java)

        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase)
    }
}
