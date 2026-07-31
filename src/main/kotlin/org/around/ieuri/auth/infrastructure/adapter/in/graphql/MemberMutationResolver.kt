package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.application.command.AgreementInput
import org.around.ieuri.auth.application.command.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.command.RegisterMemberCommand
import org.around.ieuri.auth.application.command.RequestPasswordResetCommand
import org.around.ieuri.auth.application.command.ResendVerificationCommand
import org.around.ieuri.auth.application.command.ResetPasswordCommand
import org.around.ieuri.auth.application.command.VerifyEmailCommand
import org.around.ieuri.auth.application.port.`in`.command.MemberRegistrationUseCase
import org.around.ieuri.auth.domain.vo.Provider
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
import org.around.ieuri.common.graphql.generated.types.VerifyEmailInput
import org.around.ieuri.common.graphql.generated.types.VerifyEmailPayload
import java.util.UUID

/**
 * 회원 관련 GraphQL 뮤테이션 리졸버(인바운드 어댑터). 얇게 유지하며 유스케이스에 위임한다.
 * 입력/출력 타입은 auth.graphqls로부터 DGS codegen으로 생성된다(schema-first).
 * 리졸버는 생성 타입 → 커맨드, 애플리케이션 결과 → 생성 응답 타입 매핑만 담당한다.
 */
@DgsComponent
class MemberMutationResolver(
    private val memberRegistrationUseCase: MemberRegistrationUseCase,
) {
    @DgsQuery
    fun authHealth(): String = "ok"

    @DgsMutation
    fun registerSnsMember(@InputArgument input: RegisterSnsMemberInput): RegisterSnsMemberPayload = withUserErrors(
        onError = { RegisterSnsMemberPayload(success = false, userErrors = it, emailVerified = null, verificationId = null) },
    ) {
        val command = RegisterMemberCommand(
            // 생성된 GraphQL Provider enum → 도메인 Provider (이름이 일치)
            provider = Provider.valueOf(input.provider.name),
            idToken = input.idToken,
            submittedEmail = input.email,
            ageVerified = input.ageVerified,
            // 생성된 TermsCode enum → String (도메인/커맨드는 데이터주도로 String 유지)
            agreements = input.agreements.map { AgreementInput(it.termsCode.name, it.agreed) },
        )
        val result = memberRegistrationUseCase.registerBySns(command)
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
        val command = RegisterEmailMemberCommand(
            email = input.email,
            password = input.password,
            ageVerified = input.ageVerified,
            agreements = input.agreements.map { AgreementInput(it.termsCode.name, it.agreed) },
        )
        val result = memberRegistrationUseCase.registerByEmail(command)
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
        memberRegistrationUseCase.requestPasswordReset(RequestPasswordResetCommand(input.email))
        // 계정 유무를 드러내지 않도록 항상 성공으로 응답한다.
        RequestPasswordResetPayload(success = true, userErrors = emptyList())
    }

    @DgsMutation
    fun resetPassword(@InputArgument input: ResetPasswordInput): ResetPasswordPayload = withUserErrors(
        onError = { ResetPasswordPayload(success = false, userErrors = it, failureReason = null) },
    ) {
        val result = memberRegistrationUseCase.resetPassword(
            ResetPasswordCommand(email = input.email, code = input.code, newPassword = input.newPassword),
        )
        ResetPasswordPayload(success = result.success, userErrors = emptyList(), failureReason = result.failureReason)
    }
}
