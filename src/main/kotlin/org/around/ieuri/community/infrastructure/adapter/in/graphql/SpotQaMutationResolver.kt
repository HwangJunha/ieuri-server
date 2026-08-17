package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
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
import org.around.ieuri.community.application.port.`in`.command.SpotAnswerCommandUseCase
import org.around.ieuri.community.application.port.`in`.command.SpotQuestionCommandUseCase
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toCommand
import org.springframework.stereotype.Component

/**
 * 장소 질문/답변 쓰기 리졸버(SPEC-0026). 전부 인증이 필요하며, 비로그인은
 * UnauthorizedException이 withCommunityUserErrors에 붙잡히지 않고 최상위 errors[]로 전파된다.
 *
 * 리졸버는 얇게 둔다 — 입력을 커맨드로 옮기고 결과를 payload로 옮길 뿐 판단하지 않는다.
 * 작성 자격 판정은 전부 응용 계층이 한다.
 *
 * 생성 뮤테이션이 **id만 돌려주는 것**은 커뮤니티 글과 같은 근거다 — payload에 엔티티를
 * 실으면 장소·작성자를 즉시 읽어야 해서, 방금 만든 것을 그리려고 조회가 여러 번 붙는다.
 */
@DgsComponent
@Component
class SpotQaMutationResolver(
    private val questionCommandUseCase: SpotQuestionCommandUseCase,
    private val answerCommandUseCase: SpotAnswerCommandUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    @DgsMutation
    fun createSpotQuestion(
        @InputArgument input: CreateSpotQuestionInput,
    ): CreateSpotQuestionPayload = withCommunityUserErrors(
        onError = { CreateSpotQuestionPayload(success = false, userErrors = it, questionId = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val questionId = questionCommandUseCase.createQuestion(input.toCommand(), memberId)
        CreateSpotQuestionPayload(
            success = true,
            userErrors = emptyList(),
            questionId = questionId,
        )
    }

    @DgsMutation
    fun deleteSpotQuestion(
        @InputArgument input: DeleteSpotQuestionInput,
    ): DeleteSpotQuestionPayload = withCommunityUserErrors(
        onError = { DeleteSpotQuestionPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        questionCommandUseCase.deleteQuestion(input.toCommand(), memberId)
        DeleteSpotQuestionPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun resolveSpotQuestion(
        @InputArgument input: ResolveSpotQuestionInput,
    ): ResolveSpotQuestionPayload = withCommunityUserErrors(
        onError = { ResolveSpotQuestionPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        questionCommandUseCase.resolveQuestion(input.toCommand(), memberId)
        ResolveSpotQuestionPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun createSpotAnswer(
        @InputArgument input: CreateSpotAnswerInput,
    ): CreateSpotAnswerPayload = withCommunityUserErrors(
        onError = { CreateSpotAnswerPayload(success = false, userErrors = it, answerId = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val answerId = answerCommandUseCase.createAnswer(input.toCommand(), memberId)
        CreateSpotAnswerPayload(
            success = true,
            userErrors = emptyList(),
            answerId = answerId,
        )
    }

    @DgsMutation
    fun deleteSpotAnswer(
        @InputArgument input: DeleteSpotAnswerInput,
    ): DeleteSpotAnswerPayload = withCommunityUserErrors(
        onError = { DeleteSpotAnswerPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        answerCommandUseCase.deleteAnswer(input.toCommand(), memberId)
        DeleteSpotAnswerPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun createSpotAnswerReply(
        @InputArgument input: CreateSpotAnswerReplyInput,
    ): CreateSpotAnswerReplyPayload = withCommunityUserErrors(
        onError = {
            CreateSpotAnswerReplyPayload(success = false, userErrors = it, replyId = null)
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val replyId = answerCommandUseCase.createReply(input.toCommand(), memberId)
        CreateSpotAnswerReplyPayload(
            success = true,
            userErrors = emptyList(),
            replyId = replyId,
        )
    }

    @DgsMutation
    fun deleteSpotAnswerReply(
        @InputArgument input: DeleteSpotAnswerReplyInput,
    ): DeleteSpotAnswerReplyPayload = withCommunityUserErrors(
        onError = { DeleteSpotAnswerReplyPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        answerCommandUseCase.deleteReply(input.toCommand(), memberId)
        DeleteSpotAnswerReplyPayload(success = true, userErrors = emptyList())
    }
}
