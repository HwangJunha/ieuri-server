package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.community.application.command.ReportMessagesCommand
import org.around.ieuri.community.application.exception.AlreadyReportedException
import org.around.ieuri.community.application.exception.InvalidInputException
import org.around.ieuri.community.application.exception.NotConversationParticipantException
import org.around.ieuri.community.application.exception.ReportDetailRequiredException
import org.around.ieuri.community.application.port.out.persistence.ConversationRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.MessageReportRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.MessageRepositoryPort
import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.aggregate.MessageReport
import org.around.ieuri.community.domain.vo.ReportReason
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** DM 다건 신고(SPEC-0017 R15 — AC15-1~15-6). */
class MessageReportCommandServiceTest {

    private val reporterId = 7L
    private val counterpartId = 9L
    private val conversationId = 300L

    private val messageRepository = mockk<MessageRepositoryPort>()
    private val reportRepository = mockk<MessageReportRepositoryPort>()
    private val conversationRepository = mockk<ConversationRepositoryPort>()

    private val service = MessageReportCommandService(
        messageRepository,
        reportRepository,
        conversationRepository,
    )

    private fun message(id: Long, sender: Long = counterpartId, conversation: Long = conversationId) = Message.restore(
        id = id,
        conversationId = conversation,
        senderId = sender,
        body = "메시지 $id",
        createdAt = Instant.EPOCH,
    )

    private fun conversation() = Conversation.restore(
        id = conversationId,
        memberLow = minOf(reporterId, counterpartId),
        memberHigh = maxOf(reporterId, counterpartId),
        lastMessageAt = Instant.EPOCH,
        createdAt = Instant.EPOCH,
    )

    private fun command(
        messageIds: List<Long> = listOf(10L, 11L, 12L),
        reason: ReportReason = ReportReason.ABUSE,
        detail: String? = null,
    ) = ReportMessagesCommand(messageIds = messageIds, reason = reason, detail = detail)

    private fun givenParticipatingConversation() {
        every { conversationRepository.findById(conversationId) } returns conversation()
    }

    /** AC15-1 — 3건이 **하나의 신고 건**으로 접수된다. 3번 신고한 것으로 집계되지 않는다. */
    @Test
    fun `여러 메시지가 한 건의 신고로 접수된다`() {
        every { messageRepository.findAllByIds(any()) } returns listOf(message(10L), message(11L), message(12L))
        givenParticipatingConversation()
        val saved = slot<MessageReport>()
        every { reportRepository.save(capture(saved)) } returns true

        service.reportMessages(command(), reporterId)

        verify(exactly = 1) { reportRepository.save(any()) }
        assertEquals(listOf(10L, 11L, 12L), saved.captured.messageIds)
        assertEquals(conversationId, saved.captured.conversationId)
    }

    /** AC15-2 — 다건은 허용이지 강제가 아니다. */
    @Test
    fun `메시지 1건만으로도 신고된다`() {
        every { messageRepository.findAllByIds(any()) } returns listOf(message(10L))
        givenParticipatingConversation()
        every { reportRepository.save(any()) } returns true

        service.reportMessages(command(messageIds = listOf(10L)), reporterId)

        verify(exactly = 1) { reportRepository.save(any()) }
    }

    /** AC15-3 — 빈 목록은 신고가 성립하지 않는다. */
    @Test
    fun `빈 목록은 INVALID_INPUT이다`() {
        assertFailsWith<InvalidInputException> {
            service.reportMessages(command(messageIds = emptyList()), reporterId)
        }

        verify(exactly = 0) { reportRepository.save(any()) }
    }

    /** AC15-4 — **한 신고는 한 대화에 대한 것이다.** 섞이면 운영자의 열람 범위가 넓어진다. */
    @Test
    fun `여러 대화의 메시지가 섞이면 INVALID_INPUT이다`() {
        every { messageRepository.findAllByIds(any()) } returns listOf(
            message(10L),
            message(11L, conversation = 999L),
        )

        assertFailsWith<InvalidInputException> {
            service.reportMessages(command(messageIds = listOf(10L, 11L)), reporterId)
        }

        verify(exactly = 0) { reportRepository.save(any()) }
    }

    /** AC15-5 — 자기 메시지는 신고 대상이 아니다. */
    @Test
    fun `자기 메시지가 포함되면 INVALID_INPUT이다`() {
        every { messageRepository.findAllByIds(any()) } returns listOf(
            message(10L),
            message(11L, sender = reporterId),
        )

        assertFailsWith<InvalidInputException> {
            service.reportMessages(command(messageIds = listOf(10L, 11L)), reporterId)
        }
    }

    /** 존재하지 않는 메시지가 섞이면 부분 접수하지 않고 전부 거부한다. */
    @Test
    fun `존재하지 않는 메시지가 포함되면 INVALID_INPUT이다`() {
        every { messageRepository.findAllByIds(any()) } returns listOf(message(10L))

        assertFailsWith<InvalidInputException> {
            service.reportMessages(command(messageIds = listOf(10L, 11L)), reporterId)
        }
    }

    /** AC15-6 — 이미 신고한 메시지가 포함되면 거부된다. 판정은 저장소의 유일 제약이 맡는다. */
    @Test
    fun `이미 신고한 메시지가 포함되면 ALREADY_REPORTED다`() {
        every { messageRepository.findAllByIds(any()) } returns listOf(message(10L))
        givenParticipatingConversation()
        every { reportRepository.save(any()) } returns false

        assertFailsWith<AlreadyReportedException> {
            service.reportMessages(command(messageIds = listOf(10L)), reporterId)
        }
    }

    /** 신고를 남의 대화를 들여다보는 통로로 쓸 수 없어야 한다. */
    @Test
    fun `참여자가 아닌 대화의 메시지는 신고할 수 없다`() {
        every { messageRepository.findAllByIds(any()) } returns listOf(message(10L))
        every { conversationRepository.findById(conversationId) } returns Conversation.restore(
            id = conversationId,
            memberLow = 100L,
            memberHigh = 200L,
            lastMessageAt = Instant.EPOCH,
            createdAt = Instant.EPOCH,
        )

        assertFailsWith<NotConversationParticipantException> {
            service.reportMessages(command(messageIds = listOf(10L)), reporterId)
        }
    }

    @Test
    fun `OTHER 사유에 상세가 없으면 REPORT_DETAIL_REQUIRED다`() {
        assertFailsWith<ReportDetailRequiredException> {
            service.reportMessages(command(reason = ReportReason.OTHER, detail = "  "), reporterId)
        }

        verify(exactly = 0) { messageRepository.findAllByIds(any()) }
    }
}
