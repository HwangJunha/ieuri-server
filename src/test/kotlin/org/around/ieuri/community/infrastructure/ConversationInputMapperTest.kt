package org.around.ieuri.community.infrastructure

import org.around.ieuri.common.graphql.generated.types.MarkConversationReadInput
import org.around.ieuri.common.graphql.generated.types.ReportMessagesInput
import org.around.ieuri.common.graphql.generated.types.SendMessageInput
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.around.ieuri.common.graphql.generated.types.ReportReason as GqlReportReason

/** GraphQL 입력 → Command 변환(POL-033). 필드마다 다른 값을 준다. */
class ConversationInputMapperTest {

    @Test
    fun `SendMessageInput이 커맨드로 옮겨진다`() {
        val input = SendMessageInput(
            conversationId = 11L,
            body = "안녕하세요",
            photoKey = "images/community/3/photo",
        )

        val command = input.toCommand()

        assertEquals(11L, command.conversationId)
        assertEquals("안녕하세요", command.body)
        assertEquals("images/community/3/photo", command.photoKey)
    }

    /** 사진 없이 본문만 보내면 nullable 필드가 null로 옮겨진다. */
    @Test
    fun `SendMessageInput의 사진 키가 없으면 null로 옮겨진다`() {
        val command = SendMessageInput(conversationId = 12L, body = "본문", photoKey = null).toCommand()

        assertEquals(12L, command.conversationId)
        assertEquals("본문", command.body)
        assertNull(command.photoKey)
    }

    @Test
    fun `MarkConversationReadInput이 커맨드로 옮겨진다`() {
        val command = MarkConversationReadInput(conversationId = 21L, upToMessageId = 22L).toCommand()

        assertEquals(21L, command.conversationId)
        assertEquals(22L, command.upToMessageId)
    }

    /** 생략(null)은 "마지막 메시지까지"이므로 null이 그대로 실린다. */
    @Test
    fun `MarkConversationReadInput의 읽은 지점이 없으면 null로 옮겨진다`() {
        val command = MarkConversationReadInput(conversationId = 23L, upToMessageId = null).toCommand()

        assertEquals(23L, command.conversationId)
        assertNull(command.upToMessageId)
    }

    @Test
    fun `ReportMessagesInput이 커맨드로 옮겨진다`() {
        val input = ReportMessagesInput(
            messageIds = listOf(31L, 32L),
            reason = GqlReportReason.OTHER,
            detail = "상세 사유",
        )

        val command = input.toCommand()

        assertEquals(listOf(31L, 32L), command.messageIds)
        assertEquals(ReportReason.OTHER, command.reason)
        assertEquals("상세 사유", command.detail)
    }

    @Test
    fun `ReportMessagesInput의 상세 사유가 없으면 null로 옮겨진다`() {
        val input = ReportMessagesInput(
            messageIds = listOf(33L),
            reason = GqlReportReason.SPAM,
            detail = null,
        )

        val command = input.toCommand()

        assertEquals(listOf(33L), command.messageIds)
        assertEquals(ReportReason.SPAM, command.reason)
        assertNull(command.detail)
    }
}
