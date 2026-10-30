package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.ConversationTable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationRowMappersTest {

    private val at = OffsetDateTime.of(2026, 10, 5, 0, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `대화 행의 각 열이 같은 이름의 필드로 가고 두 읽음 지점이 바뀌지 않는다`() {
        val conversation = ResultRow.createAndFillValues(
            mapOf(
                ConversationTable.id to 1L,
                ConversationTable.memberLow to 2L,
                ConversationTable.memberHigh to 3L,
                ConversationTable.lastMessageAt to at,
                ConversationTable.lastReadMessageIdLow to 4L,
                ConversationTable.lastReadMessageIdHigh to 5L,
            ),
        ).toConversation()

        assertEquals(1L, conversation.id)
        assertEquals(2L, conversation.memberLow)
        assertEquals(3L, conversation.memberHigh)
        assertEquals(at.toInstant(), conversation.lastMessageAt)
        assertEquals(4L, conversation.lastReadMessageIdLow)
        assertEquals(5L, conversation.lastReadMessageIdHigh)
    }

    @Test
    fun `아직 아무것도 읽지 않은 대화는 읽음 지점이 null이다`() {
        val conversation = ResultRow.createAndFillValues(
            mapOf(
                ConversationTable.id to 1L,
                ConversationTable.memberLow to 2L,
                ConversationTable.memberHigh to 3L,
                ConversationTable.lastMessageAt to at,
                ConversationTable.lastReadMessageIdLow to null,
                ConversationTable.lastReadMessageIdHigh to null,
            ),
        ).toConversation()

        assertNull(conversation.lastReadMessageIdLow)
        assertNull(conversation.lastReadMessageIdHigh)
    }
}
