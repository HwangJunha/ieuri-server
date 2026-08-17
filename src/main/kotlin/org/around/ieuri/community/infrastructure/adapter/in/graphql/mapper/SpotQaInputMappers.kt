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
 * GraphQL 입력 → Command 변환(SPEC-0026, POL-033).
 *
 * 리졸버 안에 인라인으로 두지 않는 이유는 **혼자 호출할 수 없어 테스트가 불가능하기**
 * 때문이다. 여기 있으면 리졸버를 세우지 않고도 필드별로 검증할 수 있다.
 *
 * **memberId를 싣지 않는다** — 호출자 신원은 인증 컨텍스트에서 오며 입력이 정하지 않는다.
 */

fun CreateSpotQuestionInput.toCommand(): CreateSpotQuestionCommand = CreateSpotQuestionCommand(
    catalogId = catalogId,
    body = body,
    photoKeys = photoKeys,
)

fun DeleteSpotQuestionInput.toCommand(): DeleteSpotQuestionCommand =
    DeleteSpotQuestionCommand(questionId = questionId)

fun ResolveSpotQuestionInput.toCommand(): ResolveSpotQuestionCommand =
    ResolveSpotQuestionCommand(questionId = questionId)

fun CreateSpotAnswerInput.toCommand(): CreateSpotAnswerCommand = CreateSpotAnswerCommand(
    questionId = questionId,
    body = body,
    photoKeys = photoKeys,
)

fun DeleteSpotAnswerInput.toCommand(): DeleteSpotAnswerCommand =
    DeleteSpotAnswerCommand(answerId = answerId)

fun CreateSpotAnswerReplyInput.toCommand(): CreateSpotAnswerReplyCommand =
    CreateSpotAnswerReplyCommand(
        parentAnswerId = parentAnswerId,
        body = body,
        photoKeys = photoKeys,
    )

fun DeleteSpotAnswerReplyInput.toCommand(): DeleteSpotAnswerReplyCommand =
    DeleteSpotAnswerReplyCommand(replyId = replyId)
