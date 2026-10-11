package org.around.ieuri.auth.adapter.graphql.resolver
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.adapter.graphql.mapper.toCommand
import org.around.ieuri.auth.adapter.graphql.support.withAuthUserErrors
import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.application.member.provided.SnsRegistrationUseCase
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

/** 회원 관련 뮤테이션 리졸버. 생성 타입 → 커맨드, 결과 → 응답 타입 매핑만 하고 유스케이스에 위임한다. */
@DgsComponent
class MemberMutationResolver(
    private val memberCommandUseCase: MemberCommandUseCase,
    private val snsRegistrationUseCase: SnsRegistrationUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    /** 인증 대기 주소 정정(SPEC-0024 R7). 인증 주체로 회원을 정하며, 미인증 게이트의 허용 목록에 들어 있다(R9). */
    @DgsMutation
    fun retargetEmailVerification(
        @InputArgument input: RetargetEmailVerificationInput,
    ): RetargetEmailVerificationPayload = withAuthUserErrors(
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
        val result = memberCommandUseCase.retargetEmailVerification(input.toCommand(), memberId)
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
    fun registerSnsMember(@InputArgument input: RegisterSnsMemberInput): RegisterSnsMemberPayload = withAuthUserErrors(
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
    fun registerEmailMember(@InputArgument input: RegisterEmailMemberInput): RegisterEmailMemberPayload = withAuthUserErrors(
        onError = { RegisterEmailMemberPayload(success = false, userErrors = it, verificationId = null) },
    ) {
        val result = memberCommandUseCase.registerByEmail(input.toCommand())
        RegisterEmailMemberPayload(
            success = true,
            userErrors = emptyList(),
            verificationId = result.verificationPublicId?.toString(),
        )
    }

    @DgsMutation
    fun verifyEmail(@InputArgument input: VerifyEmailInput): VerifyEmailPayload = withAuthUserErrors(
        onError = { VerifyEmailPayload(success = false, userErrors = it, verified = null, failureReason = null) },
    ) {
        val result = memberCommandUseCase.verifyEmail(input.toCommand())
        VerifyEmailPayload(
            success = true,
            userErrors = emptyList(),
            verified = result.verified,
            failureReason = result.failureReason,
        )
    }

    @DgsMutation
    fun resendVerification(@InputArgument input: ResendVerificationInput): ResendVerificationPayload = withAuthUserErrors(
        onError = { ResendVerificationPayload(success = false, userErrors = it, verificationId = null, expiresAt = null) },
    ) {
        val result = memberCommandUseCase.resendVerification(input.toCommand())
        ResendVerificationPayload(
            success = true,
            userErrors = emptyList(),
            verificationId = result.verificationPublicId.toString(),
            expiresAt = result.expiresAt.toString(),
        )
    }

    @DgsMutation
    fun requestPasswordReset(@InputArgument input: RequestPasswordResetInput): RequestPasswordResetPayload = withAuthUserErrors(
        onError = { RequestPasswordResetPayload(success = false, userErrors = it) },
    ) {
        memberCommandUseCase.requestPasswordReset(input.toCommand())
        RequestPasswordResetPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun resetPassword(@InputArgument input: ResetPasswordInput): ResetPasswordPayload = withAuthUserErrors(
        onError = { ResetPasswordPayload(success = false, userErrors = it, failureReason = null) },
    ) {
        val result = memberCommandUseCase.resetPassword(input.toCommand())
        ResetPasswordPayload(success = result.success, userErrors = emptyList(), failureReason = result.failureReason)
    }
}
