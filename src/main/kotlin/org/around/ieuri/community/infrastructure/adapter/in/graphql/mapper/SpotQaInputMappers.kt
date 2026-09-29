package org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerInput
import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerReplyInput
import org.around.ieuri.common.graphql.generated.types.CreateSpotQuestionInput
import org.around.ieuri.common.graphql.generated.types.DeleteSpotAnswerInput
import org.around.ieuri.common.graphql.generated.types.DeleteSpotAnswerReplyInput
import org.around.ieuri.common.graphql.generated.types.DeleteSpotQuestionInput
import org.around.ieuri.common.graphql.generated.types.ResolveSpotQuestionInput
import org.around.ieuri.community.application.command.CreateSpotAnswerCommand
import org.around.ieuri.community.application.command.CreateSpotAnswerReplyCommand
import org.around.ieuri.community.application.command.CreateSpotQuestionCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerCommand
import org.around.ieuri.community.application.command.DeleteSpotAnswerReplyCommand
import org.around.ieuri.community.application.command.DeleteSpotQuestionCommand
import org.around.ieuri.community.application.command.ResolveSpotQuestionCommand

/**
 * 장소 질문/답변 GraphQL 입력 → Command 변환(SPEC-0026, POL-033). 호출자 id는 담지 않는다.
 */

fun CreateSpotQuestionInput.toCommand(): CreateSpotQuestionCommand = CreateSpotQuestionCommand(
    catalogId = catalogId,
    body = body,
    photoKeys = photoKeys,
)

fun DeleteSpotQuestionInput.toCommand(): DeleteSpotQuestionCommand = DeleteSpotQuestionCommand(questionId = questionId)

fun ResolveSpotQuestionInput.toCommand(): ResolveSpotQuestionCommand = ResolveSpotQuestionCommand(questionId = questionId, resolved = resolved)

fun CreateSpotAnswerInput.toCommand(): CreateSpotAnswerCommand = CreateSpotAnswerCommand(
    questionId = questionId,
    body = body,
    photoKeys = photoKeys,
)

fun DeleteSpotAnswerInput.toCommand(): DeleteSpotAnswerCommand = DeleteSpotAnswerCommand(answerId = answerId)

fun CreateSpotAnswerReplyInput.toCommand(): CreateSpotAnswerReplyCommand = CreateSpotAnswerReplyCommand(
    parentAnswerId = parentAnswerId,
    body = body,
    photoKeys = photoKeys,
)

fun DeleteSpotAnswerReplyInput.toCommand(): DeleteSpotAnswerReplyCommand = DeleteSpotAnswerReplyCommand(replyId = replyId)
