package org.around.ieuri.community.application.question

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.community.application.question.provided.dto.CreateSpotAnswerCommand
import org.around.ieuri.community.application.question.provided.dto.CreateSpotAnswerReplyCommand
import org.around.ieuri.community.application.question.provided.dto.CreateSpotQuestionCommand
import org.around.ieuri.community.application.question.provided.exception.AnswerNotFoundException
import org.around.ieuri.community.application.question.provided.exception.QuestionNotFoundException
import org.around.ieuri.community.application.question.provided.exception.ReplyToReplyNotAllowedException
import org.around.ieuri.community.application.question.required.SpotQuestionRepository
import org.around.ieuri.community.application.shared.UploadedPhotoVerifier
import org.around.ieuri.community.application.shared.dto.PostSpotView
import org.around.ieuri.community.application.shared.exception.SpotNotFoundInLocaleException
import org.around.ieuri.community.application.shared.required.CommunityEventPublisherPort
import org.around.ieuri.community.application.shared.required.ObjectStoragePort
import org.around.ieuri.community.application.shared.required.SpotLookupPort
import org.around.ieuri.community.domain.question.NotAnswerOwnerException
import org.around.ieuri.community.domain.question.SpotAnswer
import org.around.ieuri.community.domain.question.SpotAnswerReply
import org.around.ieuri.community.domain.question.SpotQuestion
import org.around.ieuri.community.domain.question.event.SpotAnswerWritten
import org.around.ieuri.community.domain.question.event.SpotReplyWritten
import org.around.ieuri.community.domain.shared.ContentLanguage
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpotQuestionModifyServiceTest {

    private val now = Instant.parse("2026-10-05T00:00:00Z")
    private val repository = mockk<SpotQuestionRepository>(relaxUnitFun = true)
    private val spotLookupPort = mockk<SpotLookupPort>()
    private val objectStoragePort = mockk<ObjectStoragePort>()
    private val eventPublisher = mockk<CommunityEventPublisherPort>(relaxUnitFun = true)
    private val service = SpotQuestionModifyService(repository, spotLookupPort, UploadedPhotoVerifier(objectStoragePort), eventPublisher)

    private val question = SpotQuestion(id = 1, catalogId = 343259, authorId = 7, body = "질문", resolved = false, hidden = false, deletedAt = null)
    private val answer = SpotAnswer(id = 20, questionId = 1, catalogId = 343259, authorId = 8, body = "답변", hidden = false, deletedAt = null)
    private val reply = SpotAnswerReply(id = 30, answerId = 20, catalogId = 343259, authorId = 9, body = "답글", hidden = false, deletedAt = null)

    @Test
    fun `질문은 활성 언어에서 해소되는 장소에만 단다`() {
        every { spotLookupPort.findSummary(343259, ContentLanguage.JA) } returns null

        assertFailsWith<SpotNotFoundInLocaleException> { service.createQuestion(CreateSpotQuestionCommand(343259, "질문", emptyList()), 7, ContentLanguage.JA) }
        verify(exactly = 0) { repository.insert(any(), any()) }

        every { spotLookupPort.findSummary(343259, ContentLanguage.KO) } returns PostSpotView(1, "남산", "", null, 127.0, 37.5)
        every { repository.insert(any(), any()) } returns 1
        assertEquals(1L, service.createQuestion(CreateSpotQuestionCommand(343259, "질문", emptyList()), 7, ContentLanguage.KO))
    }

    @Test
    fun `답변을 쓰면 답변 수를 올리고 질문 작성자에게 알림 이벤트를 낸다`() {
        every { repository.findById(1) } returns question
        every { repository.insertAnswer(any(), any()) } returns 20

        assertEquals(20L, service.createAnswer(CreateSpotAnswerCommand(questionId = 1, body = "답변", photoKeys = emptyList()), memberId = 8))

        verify { repository.increaseAnswerCount(1) }
        verify { eventPublisher.publish(SpotAnswerWritten(spotId = 343259, questionId = 1, answerId = 20, actorId = 8, recipientId = 7)) }
    }

    @Test
    fun `없는 질문에는 답할 수 없다`() {
        every { repository.findById(1) } returns null

        assertFailsWith<QuestionNotFoundException> { service.createAnswer(CreateSpotAnswerCommand(1, "답변", emptyList()), 8) }
        verify(exactly = 0) { repository.increaseAnswerCount(any()) }
    }

    @Test
    fun `답글은 답변 작성자에게 알리고, 대상이 답글이면 REPLY_TO_REPLY_NOT_ALLOWED다`() {
        every { repository.findAnswer(20) } returns answer
        every { repository.findById(1) } returns question
        every { repository.insertReply(any(), any()) } returns 30

        service.createReply(CreateSpotAnswerReplyCommand(parentAnswerId = 20, body = "고마워요", photoKeys = emptyList()), memberId = 9)
        verify { eventPublisher.publish(SpotReplyWritten(spotId = 343259, questionId = 1, replyId = 30, actorId = 9, recipientId = 8)) }

        every { repository.findAnswer(30) } returns null
        every { repository.replyExists(30) } returns true
        assertFailsWith<ReplyToReplyNotAllowedException> { service.createReply(CreateSpotAnswerReplyCommand(30, "x", emptyList()), 9) }

        every { repository.findAnswer(31) } returns null
        every { repository.replyExists(31) } returns false
        assertFailsWith<AnswerNotFoundException> { service.createReply(CreateSpotAnswerReplyCommand(31, "x", emptyList()), 9) }
    }

    @Test
    fun `답변 삭제는 루트를 거쳐 작성자를 확인하고 수에 잡혀 있었을 때만 답변 수를 내린다`() {
        every { repository.findAnswer(20) } returns answer
        every { repository.findById(1) } returns question
        every { repository.deleteAnswerWasCounted(20) } returns true

        service.deleteAnswer(20, memberId = 8)
        verify(exactly = 1) { repository.decreaseAnswerCount(1) }

        assertFailsWith<NotAnswerOwnerException> { service.deleteAnswer(20, memberId = 7) }
    }

    @Test
    fun `숨겨졌던 답변을 지우면 답변 수를 내리지 않는다`() {
        every { repository.findAnswer(20) } returns answer
        every { repository.findById(1) } returns question
        every { repository.deleteAnswerWasCounted(20) } returns false

        service.deleteAnswer(20, memberId = 8)
        verify(exactly = 0) { repository.decreaseAnswerCount(any()) }
    }

    @Test
    fun `탈퇴 정리는 답글 → 답변 → 질문 순으로 루트를 거쳐 삭제 표시한다`() {
        every { repository.findReplyIdsByAuthor(7) } returns listOf(30)
        every { repository.findAnswerIdsByAuthor(7) } returns listOf(20)
        every { repository.findIdsByAuthor(7) } returns listOf(1)
        every { repository.findReply(30) } returns reply.copy(authorId = 7)
        every { repository.findByAnswerId(20) } returns question
        every { repository.findAnswer(20) } returns answer.copy(authorId = 7)
        every { repository.findById(1) } returns question
        every { repository.markReplyDeleted(30, now) } returns true
        every { repository.markAnswerDeletedWasCounted(20, now) } returns true
        every { repository.markDeleted(1, now) } returns true

        assertEquals(3, service.eraseMemberContent(7, now))

        verifyOrder {
            repository.markReplyDeleted(30, now)
            repository.markAnswerDeletedWasCounted(20, now)
            repository.decreaseAnswerCount(1)
            repository.markDeleted(1, now)
        }
    }

    @Test
    fun `탈퇴 정리에서 루트가 이미 없는 자식은 건너뛴다`() {
        every { repository.findReplyIdsByAuthor(7) } returns emptyList()
        every { repository.findAnswerIdsByAuthor(7) } returns listOf(20)
        every { repository.findIdsByAuthor(7) } returns emptyList()
        every { repository.findAnswer(20) } returns answer.copy(authorId = 7)
        every { repository.findById(1) } returns null

        assertEquals(0, service.eraseMemberContent(7, now))
        verify(exactly = 0) { repository.markAnswerDeletedWasCounted(any(), any()) }
    }
}
