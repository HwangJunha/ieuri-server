package org.around.ieuri.community.domain

import org.around.ieuri.community.domain.aggregate.Conversation
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 대화 읽음 지점(SPEC-0017 R8 보강).
 *
 * **참여자가 정렬된 쌍으로 저장되므로**(memberLow < memberHigh) 같은 사람이라도 상대가 누구냐에
 * 따라 low가 되기도 high가 되기도 한다. 그 판정이 틀리면 **A의 읽음이 B에게 기록되어** 서로의
 * 뱃지가 뒤바뀌는데, 두 컬럼 다 Long?이라 컴파일러가 잡아주지 않는다.
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

    /** **각자의 자리에 기록되어야 한다.** 뒤바뀌면 서로의 뱃지가 엉킨다. */
    @Test
    fun `읽음 지점이 참여자별로 따로 기록된다`() {
        val conversation = conversation()

        conversation.markRead(low, 100L)
        conversation.markRead(high, 200L)

        assertEquals(100L, conversation.lastReadMessageIdOf(low))
        assertEquals(200L, conversation.lastReadMessageIdOf(high))
    }

    /** 한쪽만 읽으면 다른 쪽은 그대로 null이다 — 같은 컬럼에 쓰면 여기서 깨진다. */
    @Test
    fun `한쪽의 읽음이 다른 쪽에 새지 않는다`() {
        val conversation = conversation()

        conversation.markRead(high, 300L)

        assertNull(conversation.lastReadMessageIdOf(low))
        assertEquals(300L, conversation.lastReadMessageIdOf(high))
    }

    /**
     * **읽음 지점은 뒤로 가지 않는다.** 과거 메시지를 다시 열거나 요청이 순서를 바꿔 도착해도
     * 이미 읽은 것이 안 읽음으로 되돌아가면 안 된다 — 사용자에게는 뱃지가 되살아나 보인다.
     */
    @Test
    fun `이전 메시지로는 읽음 지점이 되돌아가지 않는다`() {
        val conversation = conversation()
        conversation.markRead(low, 500L)

        conversation.markRead(low, 100L)

        assertEquals(500L, conversation.lastReadMessageIdOf(low))
    }

    /** 같은 id를 다시 보내도 그대로다 — 재시도·중복 호출이 흔한 경로다. */
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
