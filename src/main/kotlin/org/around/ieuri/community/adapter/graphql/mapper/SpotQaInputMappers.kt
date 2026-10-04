package org.around.ieuri.community.adapter.graphql.mapper

import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerInput
import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerReplyInput
import org.around.ieuri.common.graphql.generated.types.CreateSpotQuestionInput
import org.around.ieuri.community.application.answer.provided.dto.CreateSpotAnswerCommand
import org.around.ieuri.community.application.question.provided.dto.CreateSpotQuestionCommand
import org.around.ieuri.community.application.reply.provided.dto.CreateSpotAnswerReplyCommand

// 장소 질문·답변·답글 GraphQL 입력 → 커맨드. 호출자 id는 담지 않는다.

fun CreateSpotQuestionInput.toCommand(): CreateSpotQuestionCommand = CreateSpotQuestionCommand(
    catalogId = catalogId,
    body = body,
    photoKeys = photoKeys,
)

fun CreateSpotAnswerInput.toCommand(): CreateSpotAnswerCommand = CreateSpotAnswerCommand(
    questionId = questionId,
    body = body,
    photoKeys = photoKeys,
)

fun CreateSpotAnswerReplyInput.toCommand(): CreateSpotAnswerReplyCommand = CreateSpotAnswerReplyCommand(
    parentAnswerId = parentAnswerId,
    body = body,
    photoKeys = photoKeys,
)
