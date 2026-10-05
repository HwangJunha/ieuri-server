package org.around.ieuri.community.domain.messagereport

import org.around.ieuri.community.domain.conversation.Conversation
import org.around.ieuri.community.domain.conversation.Message
import org.around.ieuri.community.domain.conversation.NotConversationParticipantException
import org.around.ieuri.community.domain.shared.InvalidInputException
import org.around.ieuri.community.domain.shared.ReportDetailRequiredException
import org.around.ieuri.community.domain.shared.ReportReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageReportTest {

    private val conversation = Conversation(5, memberLow = 7, memberHigh = 9, lastMessageAt = null, lastReadMessageIdLow = null, lastReadMessageIdHigh = null)

    private fun message(id: Long, senderId: Long = 9, conversationId: Long = 5) = Message(id, conversationId, senderId, "본문", null, null, null)

    @Test
    fun `상대의 메시지를 한 신고로 접수하고 중복 id는 하나로 본다`() {
        val report = MessageReport.file(7, listOf(1, 2, 1), listOf(message(1), message(2)), conversation, ReportReason.SPAM, null)

        assertEquals(listOf(1L, 2L), report.messageIds)
        assertEquals(5L, report.conversationId)
        assertEquals(7L, report.reporterId)
    }

    @Test
    fun `판정 순서는 상세 사유 → 존재 → 소유 → 대화 일관성 → 참여다`() {
        // 상세 사유가 다른 위반보다 먼저다.
        assertFailsWith<ReportDetailRequiredException> { MessageReport.file(7, emptyList(), emptyList(), null, ReportReason.OTHER, null) }
        assertEquals(
            "신고할 메시지를 선택해 주세요.",
            assertFailsWith<InvalidInputException> { MessageReport.file(7, emptyList(), emptyList(), null, ReportReason.SPAM, null) }.message,
        )
        assertEquals(
            "신고할 수 없는 메시지가 포함되어 있습니다.",
            assertFailsWith<InvalidInputException> { MessageReport.file(7, listOf(1, 2), listOf(message(1)), conversation, ReportReason.SPAM, null) }.message,
        )
        assertEquals(
            "본인이 보낸 메시지는 신고할 수 없습니다.",
            assertFailsWith<InvalidInputException> { MessageReport.file(7, listOf(1), listOf(message(1, senderId = 7)), conversation, ReportReason.SPAM, null) }.message,
        )
        assertEquals(
            "한 번에 한 대화의 메시지만 신고할 수 있습니다.",
            assertFailsWith<InvalidInputException> {
                MessageReport.file(7, listOf(1, 2), listOf(message(1), message(2, conversationId = 6)), null, ReportReason.SPAM, null)
            }.message,
        )
        val stranger = conversation.copy(memberLow = 8)
        assertFailsWith<NotConversationParticipantException> { MessageReport.file(7, listOf(1), listOf(message(1)), stranger, ReportReason.SPAM, null) }
    }
}
