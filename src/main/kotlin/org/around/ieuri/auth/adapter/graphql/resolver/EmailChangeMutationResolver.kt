package org.around.ieuri.auth.adapter.graphql.resolver
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.auth.adapter.graphql.support.withUserErrors
import org.around.ieuri.auth.application.member.provided.EmailChangeUseCase
import org.around.ieuri.auth.application.member.provided.dto.ChangeEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendEmailChangeCommand
import org.around.ieuri.common.graphql.clientIp
import org.around.ieuri.common.graphql.generated.types.ChangeEmailInput
import org.around.ieuri.common.graphql.generated.types.ChangeEmailPayload
import org.around.ieuri.common.graphql.generated.types.ConfirmEmailChangeInput
import org.around.ieuri.common.graphql.generated.types.ConfirmEmailChangePayload
import org.around.ieuri.common.graphql.generated.types.ResendEmailChangeInput
import org.around.ieuri.common.graphql.generated.types.ResendEmailChangePayload
import org.around.ieuri.common.security.CurrentMemberProvider
import java.util.UUID

/**
 * 이메일 변경 뮤테이션 리졸버(SPEC-0011 R7·R8). 전부 본인 대상이라 권한 검사가 없다.
 * - 코드 검증 실패는 `userErrors`가 아니라 `failureReason`으로 내려간다.
 */
@DgsComponent
class EmailChangeMutationResolver(
    private val emailChangeUseCase: EmailChangeUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsMutation
    fun changeEmail(
        @InputArgument input: ChangeEmailInput,
        dfe: DataFetchingEnvironment,
    ): ChangeEmailPayload = withUserErrors(
        onError = { ChangeEmailPayload(success = false, userErrors = it, verificationId = null, expiresAt = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val result = emailChangeUseCase.changeEmail(ChangeEmailCommand(memberId, input.newEmail, dfe.clientIp()))
        ChangeEmailPayload(
            success = true,
            userErrors = emptyList(),
            verificationId = result.verificationPublicId.toString(),
            expiresAt = result.expiresAt.toString(),
        )
    }

    @DgsMutation
    fun confirmEmailChange(
        @InputArgument input: ConfirmEmailChangeInput,
    ): ConfirmEmailChangePayload = withUserErrors(
        onError = {
            ConfirmEmailChangePayload(success = false, userErrors = it, email = null, failureReason = null)
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val result = emailChangeUseCase.confirmEmailChange(
            ConfirmEmailChangeCommand(memberId, UUID.fromString(input.verificationId), input.code),
        )
        ConfirmEmailChangePayload(
            // 코드가 틀린 것은 오류가 아니라 결과다 — success는 요청 처리 여부를 뜻한다.
            success = true,
            userErrors = emptyList(),
            email = result.email,
            failureReason = result.failureReason,
        )
    }

    @DgsMutation
    fun resendEmailChange(
        @InputArgument input: ResendEmailChangeInput,
    ): ResendEmailChangePayload = withUserErrors(
        onError = {
            ResendEmailChangePayload(success = false, userErrors = it, verificationId = null, expiresAt = null)
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val result = emailChangeUseCase.resendEmailChange(
            ResendEmailChangeCommand(memberId, UUID.fromString(input.verificationId)),
        )
        ResendEmailChangePayload(
            success = true,
            userErrors = emptyList(),
            verificationId = result.verificationPublicId.toString(),
            expiresAt = result.expiresAt.toString(),
        )
    }
}
