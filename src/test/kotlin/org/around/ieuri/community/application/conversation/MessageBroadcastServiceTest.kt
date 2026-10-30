package org.around.ieuri.community.application.conversation

import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.conversation.required.ConversationEventPort
import kotlin.test.Test

class MessageBroadcastServiceTest {

    private val conversationEventPort = mockk<ConversationEventPort>(relaxUnitFun = true)
    private val service = MessageBroadcastService(conversationEventPort)

    @Test
    fun `새 메시지를 대화와 메시지 id로 브로커에 알린다`() {
        service.broadcastNewMessage(conversationId = 7L, messageId = 500L)

        verify(exactly = 1) { conversationEventPort.publishNewMessage(7L, 500L) }
    }
}
