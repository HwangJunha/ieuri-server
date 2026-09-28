package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.command.CreateSpotAnswerCommand
import org.around.ieuri.community.application.command.CreateSpotAnswerReplyCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerReplyCommand
import org.around.ieuri.community.application.exception.AnswerNotFoundException
import org.around.ieuri.community.application.exception.NotAnswerOwnerException
import org.around.ieuri.community.application.exception.NotReplyOwnerException
import org.around.ieuri.community.application.exception.QuestionNotFoundException
import org.around.ieuri.community.application.exception.ReplyNotFoundException
import org.around.ieuri.community.application.exception.ReplyToReplyNotAllowedException
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.port.out.persistence.SpotAnswerReplyRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.SpotAnswerRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionRepositoryPort
import org.around.ieuri.community.domain.aggregate.SpotAnswer
import org.around.ieuri.community.domain.aggregate.SpotAnswerReply
import org.around.ieuri.community.domain.aggregate.SpotQuestion
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 답변·답글 작성과 삭제(SPEC-0026 R4, R7, R10).
 * - 자격 게이팅 없이 로그인 회원이면 쓸 수 있다.
 * - 답글은 1단계뿐이고(R7), 숨겨진 답변을 지울 때 답변 수를 다시 내리지 않는다.
 */
class SpotAnswerCommandServiceTest {

    private val memberId = 7L
    private val catalogId = 11L
    private val questionId = 2L
    private val answerId = 9L
    private val replyId = 13L
    private val now = Instant.parse("2026-08-18T00:00:00Z")

    private val answerRepository = mockk<SpotAnswerRepositoryPort>(relaxed = true)
    private val replyRepository = mockk<SpotAnswerReplyRepositoryPort>(relaxed = true)
    private val questionRepository = mockk<SpotQuestionRepositoryPort>()
    private val counterPort = mockk<SpotQuestionCounterPort>(relaxed = true)
    private val photoKeyVerifier = mockk<SpotQaPhotoKeyVerifier>()
    private val eventPublisher = mockk<CommunityEventPublisherPort>(relaxed = true)

    private val service = SpotAnswerCommandService(
        answerRepository,
        replyRepository,
        questionRepository,
        counterPort,
        photoKeyVerifier,
        eventPublisher,
    )

    private fun givenQuestion(resolved: Boolean = false) {
        every { questionRepository.findById(questionId) } returns SpotQuestion.restore(
            id = questionId, catalogId = catalogId, authorId = 99L, body = "질문",
            photoKeys = emptyList(), resolved = resolved, answerCount = 0,
            hidden = false, deletedAt = null, createdAt = now,
        )
    }

    private fun answer(authorId: Long = memberId, hidden: Boolean = false) = SpotAnswer.restore(
        id = answerId, questionId = questionId, catalogId = catalogId, authorId = authorId,
        body = "답변", photoKeys = emptyList(), hidden = hidden, deletedAt = null, createdAt = now,
    )

    private fun givenAnswerSaveReturnsWithId() {
        every { answerRepository.save(any()) } answers {
            val a = firstArg<SpotAnswer>()
            SpotAnswer.restore(
                id = answerId, questionId = a.questionId, catalogId = a.catalogId,
                authorId = a.authorId, body = a.body, photoKeys = a.photoKeys,
                hidden = a.hidden, deletedAt = a.deletedAt, createdAt = now,
            )
        }
    }

    private fun givenReplySaveReturnsWithId() {
        every { replyRepository.save(any()) } answers {
            val r = firstArg<SpotAnswerReply>()
            SpotAnswerReply.restore(
                id = replyId, answerId = r.answerId, catalogId = r.catalogId,
                authorId = r.authorId, body = r.body, photoKeys = r.photoKeys,
                hidden = r.hidden, deletedAt = r.deletedAt, createdAt = now,
            )
        }
    }

    // ── 답변 작성(R4) ──────────────────────────────────────────────

    @Test
    fun `로그인 회원은 반응과 무관하게 답변을 작성하고 답변 수가 오른다`() {
        givenQuestion()
        givenAnswerSaveReturnsWithId()

        val created = service.createAnswer(CreateSpotAnswerCommand(questionId, "답변", emptyList()), memberId)

        assertEquals(answerId, created)
        verify { counterPort.increaseAnswerCount(questionId) }
    }

    @Test
    fun `해결된 질문에도 답변할 수 있다`() {
        givenQuestion(resolved = true)
        givenAnswerSaveReturnsWithId()

        assertEquals(
            answerId,
            service.createAnswer(CreateSpotAnswerCommand(questionId, "답변", emptyList()), memberId),
        )
    }

    @Test
    fun `없는 질문에는 답변할 수 없다`() {
        every { questionRepository.findById(questionId) } returns null

        assertFailsWith<QuestionNotFoundException> {
            service.createAnswer(CreateSpotAnswerCommand(questionId, "답변", emptyList()), memberId)
        }
        verify(exactly = 0) { answerRepository.save(any()) }
    }

    // ── 답글 작성(R7) ─────────────────────────────────────────────

    @Test
    fun `로그인 회원은 반응과 무관하게 답변에 답글을 단다`() {
        givenQuestion()
        every { answerRepository.findById(answerId) } returns answer(authorId = 99L)
        givenReplySaveReturnsWithId()

        val created = service.createReply(
            CreateSpotAnswerReplyCommand(parentAnswerId = answerId, body = "답글", photoKeys = emptyList()),
            memberId,
        )

        assertEquals(replyId, created)
        // 답글은 답변 수에 잡히지 않는다.
        verify(exactly = 0) { counterPort.increaseAnswerCount(any()) }
    }

    /** 지워진 질문 아래에는 답글을 달 수 없다(답변 행이 남아 있어도). */
    @Test
    fun `부모 질문이 지워졌으면 답글을 달 수 없다`() {
        every { questionRepository.findById(questionId) } returns null
        every { answerRepository.findById(answerId) } returns answer(authorId = 99L)

        assertFailsWith<QuestionNotFoundException> {
            service.createReply(
                CreateSpotAnswerReplyCommand(parentAnswerId = answerId, body = "답글", photoKeys = emptyList()),
                memberId,
            )
        }
        verify(exactly = 0) { replyRepository.save(any()) }
    }

    @Test
    fun `답글 id를 부모로 넘기면 REPLY_TO_REPLY_NOT_ALLOWED다`() {
        every { answerRepository.findById(replyId) } returns null
        every { replyRepository.findById(replyId) } returns SpotAnswerReply.restore(
            id = replyId, answerId = answerId, catalogId = catalogId, authorId = 99L,
            body = "답글", photoKeys = emptyList(), hidden = false, deletedAt = null, createdAt = now,
        )

        assertFailsWith<ReplyToReplyNotAllowedException> {
            service.createReply(
                CreateSpotAnswerReplyCommand(parentAnswerId = replyId, body = "답글", photoKeys = emptyList()),
                memberId,
            )
        }
    }

    @Test
    fun `아무것도 아닌 부모 id는 ANSWER_NOT_FOUND다`() {
        every { answerRepository.findById(answerId) } returns null
        every { replyRepository.findById(answerId) } returns null

        assertFailsWith<AnswerNotFoundException> {
            service.createReply(
                CreateSpotAnswerReplyCommand(parentAnswerId = answerId, body = "답글", photoKeys = emptyList()),
                memberId,
            )
        }
    }

    // ── 삭제(R10) ─────────────────────────────────────────────────

    @Test
    fun `답변을 지우면 답글까지 지우고 답변 수를 내린다`() {
        every { answerRepository.findById(answerId) } returns answer()

        service.deleteAnswer(DeleteSpotAnswerCommand(answerId), memberId)

        verify { answerRepository.deleteWithDescendants(answerId) }
        verify { counterPort.decreaseAnswerCount(questionId) }
    }

    @Test
    fun `숨겨진 답변을 지울 때는 답변 수를 다시 내리지 않는다`() {
        every { answerRepository.findById(answerId) } returns answer(hidden = true)

        service.deleteAnswer(DeleteSpotAnswerCommand(answerId), memberId)

        verify { answerRepository.deleteWithDescendants(answerId) }
        // 숨김 시점에 이미 빠졌으므로 다시 내리지 않는다.
        verify(exactly = 0) { counterPort.decreaseAnswerCount(any()) }
    }

    @Test
    fun `타인의 답변은 삭제할 수 없다`() {
        every { answerRepository.findById(answerId) } returns answer(authorId = memberId + 1)

        assertFailsWith<NotAnswerOwnerException> {
            service.deleteAnswer(DeleteSpotAnswerCommand(answerId), memberId)
        }
        verify(exactly = 0) { answerRepository.deleteWithDescendants(any()) }
        verify(exactly = 0) { counterPort.decreaseAnswerCount(any()) }
    }

    @Test
    fun `작성자는 답글을 지운다`() {
        every { replyRepository.findById(replyId) } returns SpotAnswerReply.restore(
            id = replyId, answerId = answerId, catalogId = catalogId, authorId = memberId,
            body = "답글", photoKeys = emptyList(), hidden = false, deletedAt = null, createdAt = now,
        )

        service.deleteReply(DeleteSpotAnswerReplyCommand(replyId), memberId)

        verify { replyRepository.delete(replyId) }
    }

    @Test
    fun `타인의 답글은 삭제할 수 없다`() {
        every { replyRepository.findById(replyId) } returns SpotAnswerReply.restore(
            id = replyId, answerId = answerId, catalogId = catalogId, authorId = memberId + 1,
            body = "답글", photoKeys = emptyList(), hidden = false, deletedAt = null, createdAt = now,
        )

        assertFailsWith<NotReplyOwnerException> {
            service.deleteReply(DeleteSpotAnswerReplyCommand(replyId), memberId)
        }
        verify(exactly = 0) { replyRepository.delete(any()) }
    }

    @Test
    fun `없는 답글의 삭제는 REPLY_NOT_FOUND다`() {
        every { replyRepository.findById(replyId) } returns null

        assertFailsWith<ReplyNotFoundException> {
            service.deleteReply(DeleteSpotAnswerReplyCommand(replyId), memberId)
        }
    }
}
