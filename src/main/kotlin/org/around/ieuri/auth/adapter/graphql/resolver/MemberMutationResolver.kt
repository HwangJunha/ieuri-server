package org.around.ieuri.auth.adapter.graphql.resolver
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.adapter.graphql.mapper.toCommand
import org.around.ieuri.auth.adapter.graphql.support.withUserErrors
import org.around.ieuri.auth.application.member.provided.MemberRegistrationUseCase
import org.around.ieuri.auth.application.member.provided.PasswordResetUseCase
import org.around.ieuri.auth.application.member.provided.SnsRegistrationUseCase
import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand
import org.around.ieuri.common.graphql.generated.types.RegisterEmailMemberInput
import org.around.ieuri.common.graphql.generated.types.RegisterEmailMemberPayload
import org.around.ieuri.common.graphql.generated.types.RegisterSnsMemberInput
import org.around.ieuri.common.graphql.generated.types.RegisterSnsMemberPayload
import org.around.ieuri.common.graphql.generated.types.RequestPasswordResetInput
import org.around.ieuri.common.graphql.generated.types.RequestPasswordResetPayload
import org.around.ieuri.common.graphql.generated.types.ResendVerificationInput
import org.around.ieuri.common.graphql.generated.types.ResendVerificationPayload
import org.around.ieuri.common.graphql.generated.types.ResetPasswordInput
import org.around.ieuri.common.graphql.generated.types.ResetPasswordPayload
import org.around.ieuri.common.graphql.generated.types.RetargetEmailVerificationInput
import org.around.ieuri.common.graphql.generated.types.RetargetEmailVerificationPayload
import org.around.ieuri.common.graphql.generated.types.VerifyEmailInput
import org.around.ieuri.common.graphql.generated.types.VerifyEmailPayload
import org.around.ieuri.common.security.CurrentMemberProvider
import java.util.UUID

/** 회원 관련 뮤테이션 리졸버. 생성 타입 → 커맨드, 결과 → 응답 타입 매핑만 하고 유스케이스에 위임한다. */
@DgsComponent
class MemberMutationResolver(
    private val memberRegistrationUseCase: MemberRegistrationUseCase,
    private val snsRegistrationUseCase: SnsRegistrationUseCase,
    private val passwordResetUseCase: PasswordResetUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    /** 인증 대기 주소 정정(SPEC-0024 R7). 인증 주체로 회원을 정하며, 미인증 게이트의 허용 목록에 들어 있다(R9). */
    @DgsMutation
    fun retargetEmailVerification(
        @InputArgument input: RetargetEmailVerificationInput,
    ): RetargetEmailVerificationPayload = withUserErrors(
        onError = {
            RetargetEmailVerificationPayload(
                success = false,
                userErrors = it,
                verificationId = null,
                expiresAt = null,
            )
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val result = memberRegistrationUseCase.retargetEmailVerification(
            RetargetEmailVerificationCommand(memberId, input.newEmail),
        )
        RetargetEmailVerificationPayload(
            success = true,
            userErrors = emptyList(),
            verificationId = result.verificationPublicId.toString(),
            expiresAt = result.expiresAt.toString(),
        )
    }

    @DgsQuery
    fun authHealth(): String = "ok"

    @DgsMutation
    fun registerSnsMember(@InputArgument input: RegisterSnsMemberInput): RegisterSnsMemberPayload = withUserErrors(
        onError = { RegisterSnsMemberPayload(success = false, userErrors = it, emailVerified = null, verificationId = null) },
    ) {
        val result = snsRegistrationUseCase.registerBySns(input.toCommand())
        RegisterSnsMemberPayload(
            success = true,
            userErrors = emptyList(),
            emailVerified = result.emailVerified,
            verificationId = result.verificationPublicId?.toString(),
        )
    }

    @DgsMutation
    fun registerEmailMember(@InputArgument input: RegisterEmailMemberInput): RegisterEmailMemberPayload = withUserErrors(
        onError = { RegisterEmailMemberPayload(success = false, userErrors = it, verificationId = null) },
    ) {
        val result = memberRegistrationUseCase.registerByEmail(input.toCommand())
        RegisterEmailMemberPayload(
            success = true,
            userErrors = emptyList(),
            verificationId = result.verificationPublicId?.toString(),
        )
    }

    @DgsMutation
    fun verifyEmail(@InputArgument input: VerifyEmailInput): VerifyEmailPayload = withUserErrors(
        onError = { VerifyEmailPayload(success = false, userErrors = it, verified = null, failureReason = null) },
    ) {
        val result = memberRegistrationUseCase.verifyEmail(
            VerifyEmailCommand(UUID.fromString(input.verificationId), input.code),
        )
        VerifyEmailPayload(
            success = true,
            userErrors = emptyList(),
            verified = result.verified,
            failureReason = result.failureReason,
        )
    }

    @DgsMutation
    fun resendVerification(@InputArgument input: ResendVerificationInput): ResendVerificationPayload = withUserErrors(
        onError = { ResendVerificationPayload(success = false, userErrors = it, verificationId = null, expiresAt = null) },
    ) {
        val result = memberRegistrationUseCase.resendVerification(
            ResendVerificationCommand(UUID.fromString(input.verificationId)),
        )
        ResendVerificationPayload(
            success = true,
            userErrors = emptyList(),
            verificationId = result.verificationPublicId.toString(),
            expiresAt = result.expiresAt.toString(),
        )
    }

    @DgsMutation
    fun requestPasswordReset(@InputArgument input: RequestPasswordResetInput): RequestPasswordResetPayload = withUserErrors(
        onError = { RequestPasswordResetPayload(success = false, userErrors = it) },
    ) {
        passwordResetUseCase.requestPasswordReset(RequestPasswordResetCommand(input.email))
        RequestPasswordResetPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun resetPassword(@InputArgument input: ResetPasswordInput): ResetPasswordPayload = withUserErrors(
        onError = { ResetPasswordPayload(success = false, userErrors = it, failureReason = null) },
    ) {
        val result = passwordResetUseCase.resetPassword(input.toCommand())
        ResetPasswordPayload(success = result.success, userErrors = emptyList(), failureReason = result.failureReason)
    }
}
