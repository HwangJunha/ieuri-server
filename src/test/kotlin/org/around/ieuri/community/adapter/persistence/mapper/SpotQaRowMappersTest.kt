package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.SpotAnswerReplyTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerTable
import org.around.ieuri.community.adapter.persistence.table.SpotQuestionTable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotQaRowMappersTest {

    private val deletedAt = OffsetDateTime.of(2026, 10, 5, 0, 0, 0, 0, ZoneOffset.UTC)
    private val deletedInstant = Instant.parse("2026-10-05T00:00:00Z")

    @Test
    fun `질문 행의 각 열이 같은 이름의 필드로 간다`() {
        val question = ResultRow.createAndFillValues(
            mapOf(
                SpotQuestionTable.id to 1L,
                SpotQuestionTable.catalogId to 2L,
                SpotQuestionTable.authorId to 3L,
                SpotQuestionTable.body to "질문",
                SpotQuestionTable.resolved to true,
                SpotQuestionTable.hidden to false,
                SpotQuestionTable.deletedAt to deletedAt,
            ),
        ).toSpotQuestion()

        assertEquals(1L, question.id)
        assertEquals(2L, question.catalogId)
        assertEquals(3L, question.authorId)
        assertEquals("질문", question.body)
        assertEquals(true, question.resolved)
        assertEquals(false, question.hidden)
        assertEquals(deletedInstant, question.deletedAt)
    }

    @Test
    fun `질문의 해결·숨김은 서로 바뀌지 않고 삭제 시각이 없으면 null이다`() {
        val question = ResultRow.createAndFillValues(
            mapOf(
                SpotQuestionTable.id to 1L,
                SpotQuestionTable.catalogId to 2L,
                SpotQuestionTable.authorId to 3L,
                SpotQuestionTable.body to "q",
                SpotQuestionTable.resolved to false,
                SpotQuestionTable.hidden to true,
                SpotQuestionTable.deletedAt to null,
            ),
        ).toSpotQuestion()

        assertEquals(false, question.resolved)
        assertEquals(true, question.hidden)
        assertNull(question.deletedAt)
    }

    @Test
    fun `답변 행의 각 열이 같은 이름의 필드로 간다`() {
        val answer = ResultRow.createAndFillValues(
            mapOf(
                SpotAnswerTable.id to 11L,
                SpotAnswerTable.questionId to 12L,
                SpotAnswerTable.catalogId to 13L,
                SpotAnswerTable.authorId to 14L,
                SpotAnswerTable.body to "답변",
                SpotAnswerTable.hidden to true,
                SpotAnswerTable.deletedAt to null,
            ),
        ).toSpotAnswer()

        assertEquals(11L, answer.id)
        assertEquals(12L, answer.questionId)
        assertEquals(13L, answer.catalogId)
        assertEquals(14L, answer.authorId)
        assertEquals("답변", answer.body)
        assertEquals(true, answer.hidden)
        assertNull(answer.deletedAt)
    }

    @Test
    fun `답글 행의 각 열이 같은 이름의 필드로 간다`() {
        val reply = ResultRow.createAndFillValues(
            mapOf(
                SpotAnswerReplyTable.id to 21L,
                SpotAnswerReplyTable.answerId to 22L,
                SpotAnswerReplyTable.catalogId to 23L,
                SpotAnswerReplyTable.authorId to 24L,
                SpotAnswerReplyTable.body to "답글",
                SpotAnswerReplyTable.hidden to false,
                SpotAnswerReplyTable.deletedAt to deletedAt,
            ),
        ).toSpotAnswerReply()

        assertEquals(21L, reply.id)
        assertEquals(22L, reply.answerId)
        assertEquals(23L, reply.catalogId)
        assertEquals(24L, reply.authorId)
        assertEquals("답글", reply.body)
        assertEquals(false, reply.hidden)
        assertEquals(deletedInstant, reply.deletedAt)
    }
}
