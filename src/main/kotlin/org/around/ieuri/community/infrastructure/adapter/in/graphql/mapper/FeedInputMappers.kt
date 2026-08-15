package org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.common.graphql.generated.types.CreateFeedItemInput
import org.around.ieuri.common.graphql.generated.types.MarkConversationReadInput
import org.around.ieuri.common.graphql.generated.types.ReportMessagesInput
import org.around.ieuri.common.graphql.generated.types.SendMessageInput
import org.around.ieuri.community.application.command.CreateFeedItemCommand
import org.around.ieuri.community.application.command.MarkConversationReadCommand
import org.around.ieuri.community.application.command.ReportMessagesCommand
import org.around.ieuri.community.application.command.SendMessageCommand
import org.around.ieuri.community.domain.vo.ReportReason

/**
 * GraphQL 입력 → Command 변환(SPEC-0017, POL-033).
 *
 * 리졸버 메서드 안에 인라인으로 두면 리졸버를 세우지 않고는 호출할 수 없어 테스트가 불가능하다.
 * **필드가 여럿이라 뒤바뀔 여지가 있는 입력만** 여기로 뺀다 — 인자 하나짜리는 바뀔 자리가 없다.
 *
 * 호출자(memberId)는 인증 컨텍스트에서 오므로 커맨드에 담지 않는다.
 */

fun CreateFeedItemInput.toCommand(): CreateFeedItemCommand = CreateFeedItemCommand(
    body = body,
    catalogId = catalogId,
    photoKeys = photoKeys,
)

fun SendMessageInput.toCommand(): SendMessageCommand = SendMessageCommand(
    conversationId = conversationId,
    body = body,
    feedItemId = feedItemId,
    photoKey = photoKey,
)

fun MarkConversationReadInput.toCommand(): MarkConversationReadCommand = MarkConversationReadCommand(
    conversationId = conversationId,
    upToMessageId = upToMessageId,
)

fun ReportMessagesInput.toCommand(): ReportMessagesCommand = ReportMessagesCommand(
    messageIds = messageIds,
    reason = ReportReason.valueOf(reason.name),
    detail = detail,
)
