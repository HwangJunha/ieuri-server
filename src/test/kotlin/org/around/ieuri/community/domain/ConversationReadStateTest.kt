package org.around.ieuri.community.domain

import org.around.ieuri.community.domain.aggregate.Conversation
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 대화 읽음 지점(SPEC-0017 R8).
 * - 참여자는 정렬된 쌍이라 low/high 자리 판정이 틀리면 서로의 읽음이 뒤바뀐다.
 */
class ConversationReadStateTest {

    private val low = 10L
    private val high = 20L

    private fun conversation() = Conversation.restore(
        id = 1L,
        memberLow = low,
        memberHigh = high,
        lastMessageAt = Instant.EPOCH,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun `새 대화는 양쪽 다 읽은 지점이 없다`() {
        val conversation = conversation()

        assertNull(conversation.lastReadMessageIdOf(low))
        assertNull(conversation.lastReadMessageIdOf(high))
    }

    /** 각자의 자리에 기록된다. */
    @Test
    fun `읽음 지점이 참여자별로 따로 기록된다`() {
        val conversation = conversation()

        conversation.markRead(low, 100L)
        conversation.markRead(high, 200L)

        assertEquals(100L, conversation.lastReadMessageIdOf(low))
        assertEquals(200L, conversation.lastReadMessageIdOf(high))
    }

    /** 한쪽만 읽으면 다른 쪽은 그대로 null이다. */
    @Test
    fun `한쪽의 읽음이 다른 쪽에 새지 않는다`() {
        val conversation = conversation()

        conversation.markRead(high, 300L)

        assertNull(conversation.lastReadMessageIdOf(low))
        assertEquals(300L, conversation.lastReadMessageIdOf(high))
    }

    /** 읽음 지점은 뒤로 가지 않는다. */
    @Test
    fun `이전 메시지로는 읽음 지점이 되돌아가지 않는다`() {
        val conversation = conversation()
        conversation.markRead(low, 500L)

        conversation.markRead(low, 100L)

        assertEquals(500L, conversation.lastReadMessageIdOf(low))
    }

    /** 같은 id를 다시 보내도 그대로다. */
    @Test
    fun `같은 지점을 다시 읽어도 그대로다`() {
        val conversation = conversation()
        conversation.markRead(low, 500L)

        conversation.markRead(low, 500L)

        assertEquals(500L, conversation.lastReadMessageIdOf(low))
    }

    @Test
    fun `참여자가 아니면 읽음 처리할 수 없다`() {
        val conversation = conversation()

        assertFailsWith<IllegalArgumentException> { conversation.markRead(999L, 100L) }
        assertFailsWith<IllegalArgumentException> { conversation.lastReadMessageIdOf(999L) }
    }
}
