package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.ConversationTable
import org.around.ieuri.community.adapter.persistence.table.MessageQuoteTable
import org.around.ieuri.community.adapter.persistence.table.MessageTable
import org.around.ieuri.community.domain.conversation.SpotQaOrigin
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `메시지 행은 복호화한 본문·인용을 받아 각 필드로 간다`() {
        val key = "images/community/12/${UUID.randomUUID()}"
        val origin = SpotQaOrigin(catalogId = 1, spotTitle = "남산", questionId = 2, questionExcerpt = "질문", answerId = 3, answerExcerpt = "답변")
        val message = ResultRow.createAndFillValues(
            mapOf(
                MessageTable.id to 10L,
                MessageTable.conversationId to 11L,
                MessageTable.senderId to 12L,
                MessageTable.photoObjectKey to key,
                MessageTable.createdAt to at,
            ),
        ).toMessage(decryptedBody = "평문", spotQaOrigin = origin)

        assertEquals(10L, message.id)
        assertEquals(11L, message.conversationId)
        assertEquals(12L, message.senderId)
        assertEquals("평문", message.body)
        assertEquals(key, message.photoKey?.value)
        assertEquals(12L, message.photoKey?.memberId)
        assertEquals(origin, message.spotQaOrigin)
        assertEquals(at.toInstant(), message.createdAt)
    }

    @Test
    fun `사진·인용 없는 메시지는 null이고 손상된 사진 키는 실패한다`() {
        val row = mapOf(MessageTable.id to 1L, MessageTable.conversationId to 2L, MessageTable.senderId to 3L, MessageTable.createdAt to at)

        val message = ResultRow.createAndFillValues(row + (MessageTable.photoObjectKey to null)).toMessage("", null)
        assertNull(message.photoKey)
        assertNull(message.spotQaOrigin)

        assertFailsWith<IllegalArgumentException> { ResultRow.createAndFillValues(row + (MessageTable.photoObjectKey to "bad")).toMessage("", null) }
    }

    @Test
    fun `인용 행은 장소명·질문 id·답변 id·카탈로그를 섞지 않고 복호화한 발췌를 받는다`() {
        val origin = ResultRow.createAndFillValues(
            mapOf(
                MessageQuoteTable.contextId to 101L,
                MessageQuoteTable.titleSnapshot to "남산",
                MessageQuoteTable.parentId to 102L,
                MessageQuoteTable.targetId to 103L,
            ),
        ).toSpotQaOrigin(decryptedQuestionExcerpt = "질문 발췌", decryptedAnswerExcerpt = "답변 발췌")

        assertEquals(101L, origin.catalogId)
        assertEquals("남산", origin.spotTitle)
        assertEquals(102L, origin.questionId)
        assertEquals("질문 발췌", origin.questionExcerpt)
        assertEquals(103L, origin.answerId)
        assertEquals("답변 발췌", origin.answerExcerpt)
    }

    @Test
    fun `카탈로그나 질문 id가 빠진 인용은 데이터 손상이다`() {
        val row = mapOf(MessageQuoteTable.titleSnapshot to "남산", MessageQuoteTable.targetId to 103L)

        assertFailsWith<IllegalArgumentException> {
            ResultRow.createAndFillValues(row + (MessageQuoteTable.contextId to null) + (MessageQuoteTable.parentId to 102L)).toSpotQaOrigin("q", "a")
        }
        assertFailsWith<IllegalArgumentException> {
            ResultRow.createAndFillValues(row + (MessageQuoteTable.contextId to 101L) + (MessageQuoteTable.parentId to null)).toSpotQaOrigin("q", "a")
        }
    }
}
