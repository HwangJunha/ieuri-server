package org.around.ieuri.community.domain.question

import org.around.ieuri.community.domain.shared.InvalidInputException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SpotQuestionTest {

    private val now = Instant.parse("2026-10-05T00:00:00Z")
    private val question = SpotQuestion(id = 1, catalogId = 343259, authorId = 7, body = "질문", resolved = false, hidden = false, deletedAt = null)
    private val answer = SpotAnswer(id = 20, questionId = 1, catalogId = 343259, authorId = 8, body = "답변", hidden = false, deletedAt = null)
    private val reply = SpotAnswerReply(id = 30, answerId = 20, catalogId = 343259, authorId = 9, body = "답글", hidden = false, deletedAt = null)

    @Test
    fun `새 질문은 본문을 트리밍하고 미해결이다`() {
        val written = SpotQuestion.write(catalogId = 343259, authorId = 7, body = "  어디가 좋아요?  ")

        assertNull(written.id)
        assertEquals("어디가 좋아요?", written.body)
        assertEquals(false, written.resolved)
    }

    @Test
    fun `본문은 비거나 500자를 넘으면 INVALID_INPUT이다`() {
        assertFailsWith<InvalidInputException> { SpotQuestion.write(1, 7, "  ") }
        assertFailsWith<InvalidInputException> { SpotQuestion.write(1, 7, "가".repeat(SpotQaText.MAX_LENGTH + 1)) }
        assertFailsWith<InvalidInputException> { question.answer(8, "가".repeat(SpotQaText.MAX_LENGTH + 1)) }
    }

    @Test
    fun `해결 표시는 작성자만 양방향으로 바꾼다`() {
        assertEquals(true, question.resolve(7, true).resolved)
        assertEquals(false, question.copy(resolved = true).resolve(7, false).resolved)
        assertFailsWith<NotQuestionOwnerException> { question.resolve(8, true) }
    }

    @Test
    fun `답변은 루트가 만들고 질문 id와 장소를 루트에서 받는다`() {
        val written = question.answer(authorId = 8, body = " 남산이요 ")

        assertEquals(1L, written.questionId)
        assertEquals(343259L, written.catalogId)
        assertEquals(8L, written.authorId)
        assertEquals("남산이요", written.body)
    }

    @Test
    fun `답글은 이 질문의 답변에만 단다`() {
        val written = question.reply(answer, authorId = 9, body = "고마워요")

        assertEquals(20L, written.answerId)
        assertEquals(343259L, written.catalogId)
        assertFailsWith<IllegalArgumentException> { question.reply(answer.copy(questionId = 2), 9, "다른 질문의 답변") }
    }

    @Test
    fun `답변·답글 삭제는 각 작성자만 한다`() {
        question.removeAnswer(answer, memberId = 8)
        question.removeReply(reply, memberId = 9)
        assertFailsWith<NotAnswerOwnerException> { question.removeAnswer(answer, memberId = 7) }
        assertFailsWith<NotReplyOwnerException> { question.removeReply(reply, memberId = 7) }
    }

    @Test
    fun `탈퇴는 삭제 표시만 하며 작성자 확인을 거친다`() {
        assertEquals(now, question.withdraw(7, now).deletedAt)
        assertEquals(now, question.withdrawAnswer(answer, 8, now).deletedAt)
        assertEquals(now, question.withdrawReply(reply, 9, now).deletedAt)
        assertFailsWith<NotQuestionOwnerException> { question.withdraw(8, now) }
        assertFailsWith<NotAnswerOwnerException> { question.withdrawAnswer(answer, 9, now) }
    }

    @Test
    fun `각 엔티티는 자기 종류를 고정으로 든다`() {
        assertEquals(SpotQaKind.QUESTION, question.kind)
        assertEquals(SpotQaKind.ANSWER, answer.kind)
        assertEquals(SpotQaKind.REPLY, reply.kind)
    }
}
