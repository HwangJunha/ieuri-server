package org.around.ieuri.community.application.messagereport

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.conversation.required.ConversationRepository
import org.around.ieuri.community.application.messagereport.provided.dto.ReportMessagesCommand
import org.around.ieuri.community.application.messagereport.required.MessageReportRepository
import org.around.ieuri.community.domain.conversation.Conversation
import org.around.ieuri.community.domain.conversation.Message
import org.around.ieuri.community.domain.shared.AlreadyReportedException
import org.around.ieuri.community.domain.shared.InvalidInputException
import org.around.ieuri.community.domain.shared.ReportReason
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MessageReportModifyServiceTest {

    private val reports = mockk<MessageReportRepository>()
    private val conversations = mockk<ConversationRepository>()
    private val service = MessageReportModifyService(reports, conversations)

    private val conversation = Conversation(5, memberLow = 7, memberHigh = 9, lastMessageAt = null, lastReadMessageIdLow = null, lastReadMessageIdHigh = null)

    private fun message(id: Long, conversationId: Long = 5) = Message(id, conversationId, senderId = 9, body = "본문", photoKey = null, spotQaOrigin = null, createdAt = null)

    @Test
    fun `메시지들의 대화를 읽어 신고를 저장하고, 이미 신고한 메시지가 있으면 ALREADY_REPORTED다`() {
        every { conversations.findMessages(listOf(1, 2)) } returns listOf(message(1), message(2))
        every { conversations.findById(5) } returns conversation
        every { reports.insert(any()) } returnsMany listOf(true, false)

        service.reportMessages(ReportMessagesCommand(listOf(1, 2, 2), ReportReason.SPAM, null), memberId = 7)
        verify { reports.insert(match { it.messageIds == listOf(1L, 2L) && it.conversationId == 5L }) }

        assertFailsWith<AlreadyReportedException> { service.reportMessages(ReportMessagesCommand(listOf(1, 2), ReportReason.SPAM, null), 7) }
    }

    @Test
    fun `여러 대화의 메시지가 섞이면 대화를 읽지 않고 거부한다`() {
        every { conversations.findMessages(listOf(1, 2)) } returns listOf(message(1), message(2, conversationId = 6))

        assertFailsWith<InvalidInputException> { service.reportMessages(ReportMessagesCommand(listOf(1, 2), ReportReason.SPAM, null), 7) }
        verify(exactly = 0) { conversations.findById(any()) }
        verify(exactly = 0) { reports.insert(any()) }
    }
}
