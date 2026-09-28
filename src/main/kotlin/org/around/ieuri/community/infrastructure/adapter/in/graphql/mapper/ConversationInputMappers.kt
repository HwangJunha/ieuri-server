package org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.common.graphql.generated.types.MarkConversationReadInput
import org.around.ieuri.common.graphql.generated.types.ReportMessagesInput
import org.around.ieuri.common.graphql.generated.types.SendMessageInput
import org.around.ieuri.common.graphql.generated.types.StartConversationInput
import org.around.ieuri.community.application.command.MarkConversationReadCommand
import org.around.ieuri.community.application.command.ReportMessagesCommand
import org.around.ieuri.community.application.command.SendMessageCommand
import org.around.ieuri.community.application.command.StartConversationCommand
import org.around.ieuri.community.domain.vo.ReportReason

/*
 * 대화 GraphQL 입력 → Command 변환(SPEC-0017·SPEC-0021, POL-033). 호출자 id는 담지 않는다.
 */

fun StartConversationInput.toCommand(): StartConversationCommand = StartConversationCommand(counterpartMemberId = counterpartMemberId)

/** 메시지 전송. `sourceAnswerId`가 없으면 인용 없는 메시지다. 무효한 답변 id는 응용 계층이 무시한다(AC-13.4). */
fun SendMessageInput.toCommand(): SendMessageCommand = SendMessageCommand(
    conversationId = conversationId,
    body = body,
    photoKey = photoKey,
    sourceAnswerId = sourceAnswerId,
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
