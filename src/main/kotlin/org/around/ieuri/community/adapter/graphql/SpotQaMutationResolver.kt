package org.around.ieuri.community.adapter.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerInput
import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerPayload
import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerReplyInput
import org.around.ieuri.common.graphql.generated.types.CreateSpotAnswerReplyPayload
import org.around.ieuri.common.graphql.generated.types.CreateSpotQuestionInput
import org.around.ieuri.common.graphql.generated.types.CreateSpotQuestionPayload
import org.around.ieuri.common.graphql.generated.types.DeleteSpotAnswerInput
import org.around.ieuri.common.graphql.generated.types.DeleteSpotAnswerPayload
import org.around.ieuri.common.graphql.generated.types.DeleteSpotAnswerReplyInput
import org.around.ieuri.common.graphql.generated.types.DeleteSpotAnswerReplyPayload
import org.around.ieuri.common.graphql.generated.types.DeleteSpotQuestionInput
import org.around.ieuri.common.graphql.generated.types.DeleteSpotQuestionPayload
import org.around.ieuri.common.graphql.generated.types.ResolveSpotQuestionInput
import org.around.ieuri.common.graphql.generated.types.ResolveSpotQuestionPayload
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.adapter.graphql.contentLanguage
import org.around.ieuri.community.adapter.graphql.mapper.toCommand
import org.around.ieuri.community.adapter.graphql.withCommunityUserErrors
import org.around.ieuri.community.application.question.provided.SpotQuestionCommandUseCase
import org.springframework.stereotype.Component

/**
 * 장소 질문·답변·답글 쓰기 리졸버. 전부 인증이 필요하고, 생성 뮤테이션은 id만 돌려준다.
 * - 비로그인의 UnauthorizedException은 userErrors가 아니라 최상위 errors[]로 전파된다.
 */
@DgsComponent
@Component
class SpotQaMutationResolver(
    private val questionCommandUseCase: SpotQuestionCommandUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    /** 활성 언어(Accept-Language)에서 해소되는 장소에만 질문을 달 수 있다. */
    @DgsMutation
    fun createSpotQuestion(@InputArgument input: CreateSpotQuestionInput, dfe: DgsDataFetchingEnvironment): CreateSpotQuestionPayload = withCommunityUserErrors(
        onError = { CreateSpotQuestionPayload(success = false, userErrors = it, questionId = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val questionId = questionCommandUseCase.createQuestion(input.toCommand(), memberId, dfe.contentLanguage())
        CreateSpotQuestionPayload(success = true, userErrors = emptyList(), questionId = questionId)
    }

    @DgsMutation
    fun deleteSpotQuestion(@InputArgument input: DeleteSpotQuestionInput): DeleteSpotQuestionPayload = withCommunityUserErrors(
        onError = { DeleteSpotQuestionPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        questionCommandUseCase.deleteQuestion(input.questionId, memberId)
        DeleteSpotQuestionPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun resolveSpotQuestion(@InputArgument input: ResolveSpotQuestionInput): ResolveSpotQuestionPayload = withCommunityUserErrors(
        onError = { ResolveSpotQuestionPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        questionCommandUseCase.resolveQuestion(input.questionId, input.resolved, memberId)
        ResolveSpotQuestionPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun createSpotAnswer(@InputArgument input: CreateSpotAnswerInput): CreateSpotAnswerPayload = withCommunityUserErrors(
        onError = { CreateSpotAnswerPayload(success = false, userErrors = it, answerId = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val answerId = questionCommandUseCase.createAnswer(input.toCommand(), memberId)
        CreateSpotAnswerPayload(success = true, userErrors = emptyList(), answerId = answerId)
    }

    @DgsMutation
    fun deleteSpotAnswer(@InputArgument input: DeleteSpotAnswerInput): DeleteSpotAnswerPayload = withCommunityUserErrors(
        onError = { DeleteSpotAnswerPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        questionCommandUseCase.deleteAnswer(input.answerId, memberId)
        DeleteSpotAnswerPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun createSpotAnswerReply(@InputArgument input: CreateSpotAnswerReplyInput): CreateSpotAnswerReplyPayload = withCommunityUserErrors(
        onError = { CreateSpotAnswerReplyPayload(success = false, userErrors = it, replyId = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val replyId = questionCommandUseCase.createReply(input.toCommand(), memberId)
        CreateSpotAnswerReplyPayload(success = true, userErrors = emptyList(), replyId = replyId)
    }

    @DgsMutation
    fun deleteSpotAnswerReply(@InputArgument input: DeleteSpotAnswerReplyInput): DeleteSpotAnswerReplyPayload = withCommunityUserErrors(
        onError = { DeleteSpotAnswerReplyPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        questionCommandUseCase.deleteReply(input.replyId, memberId)
        DeleteSpotAnswerReplyPayload(success = true, userErrors = emptyList())
    }
}
