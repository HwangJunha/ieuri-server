package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.application.command.AgreementInput
import org.around.ieuri.auth.application.command.RegisterMemberCommand
import org.around.ieuri.auth.application.command.ResendVerificationCommand
import org.around.ieuri.auth.application.command.VerifyEmailCommand
import org.around.ieuri.auth.application.port.`in`.command.RegisterMemberUseCase
import org.around.ieuri.auth.application.port.`in`.command.ResendVerificationUseCase
import org.around.ieuri.auth.application.port.`in`.command.VerifyEmailUseCase
import org.around.ieuri.auth.domain.vo.Provider
import java.util.UUID

/**
 * 회원 관련 GraphQL 뮤테이션 리졸버(인바운드 어댑터). 얇게 유지하며 유스케이스에 위임한다.
 * GraphQL 입력 → 커맨드, 애플리케이션 결과 → 응답 페이로드 매핑을 이 계층이 소유한다.
 */
@DgsComponent
class MemberMutationResolver(
    private val registerMemberUseCase: RegisterMemberUseCase,
    private val verifyEmailUseCase: VerifyEmailUseCase,
    private val resendVerificationUseCase: ResendVerificationUseCase,
) {
    @DgsQuery
    fun authHealth(): String = "ok"

    @DgsMutation
    fun registerMember(@InputArgument input: RegisterMemberInputDto): RegisterMemberPayload {
        val command = RegisterMemberCommand(
            provider = Provider.valueOf(input.provider.uppercase()),
            idToken = input.idToken,
            submittedEmail = input.email,
            ageVerified = input.ageVerified,
            agreements = input.agreements.map { AgreementInput(it.termsCode, it.agreed) },
        )
        val result = registerMemberUseCase.handle(command)
        return RegisterMemberPayload(
            emailVerified = result.emailVerified,
            verificationId = result.verificationPublicId?.toString(),
        )
    }

    @DgsMutation
    fun verifyEmail(@InputArgument input: VerifyEmailInputDto): VerifyEmailPayload {
        val result = verifyEmailUseCase.handle(
            VerifyEmailCommand(UUID.fromString(input.verificationId), input.code),
        )
        return VerifyEmailPayload(verified = result.verified, failureReason = result.failureReason)
    }

    @DgsMutation
    fun resendVerification(@InputArgument input: ResendVerificationInputDto): ResendVerificationPayload {
        val result = resendVerificationUseCase.handle(
            ResendVerificationCommand(UUID.fromString(input.verificationId)),
        )
        return ResendVerificationPayload(
            verificationId = result.verificationPublicId.toString(),
            expiresAt = result.expiresAt.toString(),
        )
    }
}
