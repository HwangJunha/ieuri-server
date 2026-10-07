package org.around.ieuri.community.domain.conversation

import org.around.ieuri.community.domain.photo.PhotoObjectKey
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class ConversationTest {

    private val conversation = Conversation(
        id = 5,
        memberLow = 7,
        memberHigh = 9,
        lastMessageAt = Instant.parse("2026-10-05T00:00:00Z"),
        lastReadMessageIdLow = 100,
        lastReadMessageIdHigh = null,
    )

    @Test
    fun `대화는 인자 순서와 무관하게 정렬된 쌍이다`() {
        val started = Conversation.start(memberId = 9, counterpartId = 7)

        assertEquals(7L, started.memberLow)
        assertEquals(9L, started.memberHigh)
        assertNull(started.id)
    }

    @Test
    fun `자기 자신과는 대화할 수 없다`() {
        assertFailsWith<SelfConversationException> { Conversation.start(7, 7) }
    }

    @Test
    fun `참여자의 상대는 다른 한쪽이고 참여자가 아니면 NOT_CONVERSATION_PARTICIPANT다`() {
        assertEquals(9L, conversation.counterpartOf(7))
        assertEquals(7L, conversation.counterpartOf(9))
        assertFailsWith<NotConversationParticipantException> { conversation.counterpartOf(8) }
    }

    @Test
    fun `읽음 지점은 앞으로만 간다`() {
        assertEquals(150L, conversation.markRead(7, 150).lastReadMessageIdLow)
        assertSame(conversation, conversation.markRead(7, 90))
        assertEquals(40L, conversation.markRead(9, 40).lastReadMessageIdHigh)
        assertEquals(100L, conversation.markRead(9, 40).lastReadMessageIdLow)
    }

    @Test
    fun `메시지는 참여자만 보내며 본문을 트리밍해 이 대화에 붙인다`() {
        val message = conversation.send(senderId = 9, body = "  안녕 ", photoKey = null, spotQaOrigin = null)

        assertEquals(5L, message.conversationId)
        assertEquals(9L, message.senderId)
        assertEquals("안녕", message.body)
        assertFailsWith<NotConversationParticipantException> { conversation.send(8, "안녕", null, null) }
    }

    @Test
    fun `본문·사진·인용이 모두 비거나 본문이 상한을 넘으면 거부한다`() {
        assertFailsWith<IllegalArgumentException> { conversation.send(7, "  ", null, null) }
        assertFailsWith<IllegalArgumentException> { conversation.send(7, "가".repeat(Message.BODY_MAX_LENGTH + 1), null, null) }
    }

    @Test
    fun `사진만 있는 메시지는 되지만 남의 키나 확정 안 된 키는 거부한다`() {
        val mine = PhotoObjectKey.issue(7, UUID.randomUUID())

        assertEquals("", conversation.send(7, "", mine.toConfirmed(), null).body)
        assertFailsWith<IllegalArgumentException> { conversation.send(7, "", PhotoObjectKey.issue(9, UUID.randomUUID()).toConfirmed(), null) }
        assertFailsWith<IllegalArgumentException> { conversation.send(7, "", mine, null) }
    }

    @Test
    fun `보관 기간은 메시지 생성 시각 기준 180일이다`() {
        val now = Instant.parse("2026-10-05T00:00:00Z")

        assertEquals(now.minus(Duration.ofDays(180)), Message.retentionCutoff(now))
    }
}
