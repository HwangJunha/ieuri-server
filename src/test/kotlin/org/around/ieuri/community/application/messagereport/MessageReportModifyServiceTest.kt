package org.around.ieuri.community.application.messagereport

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.conversation.provided.ConversationQueryUseCase
import org.around.ieuri.community.application.conversation.provided.dto.ConversationParticipantsView
import org.around.ieuri.community.application.conversation.provided.dto.MessageOwnerView
import org.around.ieuri.community.application.messagereport.provided.dto.ReportMessagesCommand
import org.around.ieuri.community.application.messagereport.required.MessageReportRepository
import org.around.ieuri.community.domain.conversation.NotConversationParticipantException
import org.around.ieuri.community.domain.shared.AlreadyReportedException
import org.around.ieuri.community.domain.shared.InvalidInputException
import org.around.ieuri.community.domain.shared.ReportReason
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MessageReportModifyServiceTest {

    private val reports = mockk<MessageReportRepository>()
    private val conversations = mockk<ConversationQueryUseCase>()
    private val service = MessageReportModifyService(reports, conversations)

    private fun owner(id: Long, conversationId: Long = 5) = MessageOwnerView(messageId = id, conversationId = conversationId, senderId = 9)

    @Test
    fun `메시지들의 대화를 읽어 신고를 저장하고, 이미 신고한 메시지가 있으면 ALREADY_REPORTED다`() {
        every { conversations.messageOwners(listOf(1, 2)) } returns listOf(owner(1), owner(2))
        every { conversations.participantsOf(5) } returns ConversationParticipantsView(conversationId = 5, memberLow = 7, memberHigh = 9)
        every { reports.insert(any()) } returnsMany listOf(true, false)

        service.reportMessages(ReportMessagesCommand(listOf(1, 2, 2), ReportReason.SPAM, null), memberId = 7)

        verify { reports.insert(match { it.messageIds == listOf(1L, 2L) && it.conversationId == 5L }) }
        assertFailsWith<AlreadyReportedException> { service.reportMessages(ReportMessagesCommand(listOf(1, 2), ReportReason.SPAM, null), 7) }
    }

    @Test
    fun `여러 대화의 메시지가 섞이면 대화를 읽지 않고 거부한다`() {
        every { conversations.messageOwners(listOf(1, 2)) } returns listOf(owner(1), owner(2, conversationId = 6))

        assertFailsWith<InvalidInputException> { service.reportMessages(ReportMessagesCommand(listOf(1, 2), ReportReason.SPAM, null), 7) }

        verify(exactly = 0) { conversations.participantsOf(any()) }
        verify(exactly = 0) { reports.insert(any()) }
    }

    @Test
    fun `대화의 참여자가 아니면 NOT_CONVERSATION_PARTICIPANT다`() {
        every { conversations.messageOwners(listOf(1)) } returns listOf(owner(1))
        every { conversations.participantsOf(5) } returns ConversationParticipantsView(conversationId = 5, memberLow = 8, memberHigh = 9)

        assertFailsWith<NotConversationParticipantException> { service.reportMessages(ReportMessagesCommand(listOf(1), ReportReason.SPAM, null), 7) }
    }
}
