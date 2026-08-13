package org.around.ieuri.community.infrastructure

import org.around.ieuri.common.graphql.generated.types.CreateFeedItemInput
import org.around.ieuri.common.graphql.generated.types.MarkConversationReadInput
import org.around.ieuri.common.graphql.generated.types.ReportMessagesInput
import org.around.ieuri.common.graphql.generated.types.SendMessageInput
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.around.ieuri.common.graphql.generated.types.ReportReason as GqlReportReason

/**
 * GraphQL 입력 → Command 변환(SPEC-0017).
 *
 * **필드마다 다른 값을 준다** — 같은 타입의 두 필드가 뒤바뀌어도 값이 같으면 통과하기 때문이다.
 */
class FeedInputMapperTest {

    @Test
    fun `피드 생성 입력이 커맨드로 변환된다`() {
        val input = CreateFeedItemInput(
            body = "오늘의 경복궁",
            spotId = 42L,
            photoKeys = listOf("images/community/7/a", "images/community/7/b"),
        )

        val command = input.toCommand()

        assertEquals("오늘의 경복궁", command.body)
        assertEquals(42L, command.spotId)
        assertEquals(listOf("images/community/7/a", "images/community/7/b"), command.photoKeys)
    }

    /** conversationId와 body는 타입이 달라 뒤바뀔 수 없지만, 값 누락은 잡을 수 있다. */
    @Test
    fun `메시지 전송 입력이 커맨드로 변환된다`() {
        val input = SendMessageInput(conversationId = 300L, body = "안녕하세요")

        val command = input.toCommand()

        assertEquals(300L, command.conversationId)
        assertEquals("안녕하세요", command.body)
    }

    @Test
    fun `신고 입력이 커맨드로 변환된다`() {
        val input = ReportMessagesInput(
            messageIds = listOf(10L, 11L, 12L),
            reason = GqlReportReason.ABUSE,
            detail = "욕설이 심합니다",
        )

        val command = input.toCommand()

        assertEquals(listOf(10L, 11L, 12L), command.messageIds)
        assertEquals(ReportReason.ABUSE, command.reason)
        assertEquals("욕설이 심합니다", command.detail)
    }

    @Test
    fun `상세 사유가 없는 신고 입력도 변환된다`() {
        val input = ReportMessagesInput(
            messageIds = listOf(10L),
            reason = GqlReportReason.SPAM,
            detail = null,
        )

        val command = input.toCommand()

        assertEquals(listOf(10L), command.messageIds)
        assertEquals(ReportReason.SPAM, command.reason)
        assertNull(command.detail)
    }

    /** 모든 사유가 도메인 열거값과 일대일로 대응해야 한다 — 하나라도 어긋나면 변환이 터진다. */
    @Test
    fun `모든 신고 사유가 도메인 값으로 변환된다`() {
        GqlReportReason.entries.forEach { reason ->
            val command = ReportMessagesInput(listOf(10L), reason, "상세").toCommand()
            assertEquals(reason.name, command.reason.name)
        }
    }

    /**
     * 두 필드 다 ID(String→Long)라 **뒤바뀌어도 컴파일된다** — 대화 id로 읽음 지점을 잡으면
     * 엉뚱한 메시지까지 읽음 처리된다. 그래서 값을 서로 다르게 준다.
     */
    @Test
    fun `읽음 처리 입력이 커맨드로 변환된다`() {
        val command = MarkConversationReadInput(
            conversationId = 11L,
            upToMessageId = 22L,
        ).toCommand()

        assertEquals(11L, command.conversationId)
        assertEquals(22L, command.upToMessageId)
    }

    /** upToMessageId는 선택이다 — 생략하면 "마지막까지"를 뜻하는 null이 그대로 실려야 한다. */
    @Test
    fun `읽을 지점이 없으면 null로 변환된다`() {
        val command = MarkConversationReadInput(conversationId = 11L, upToMessageId = null).toCommand()

        assertEquals(11L, command.conversationId)
        assertNull(command.upToMessageId)
    }
}
