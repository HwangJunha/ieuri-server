package org.around.ieuri.auth.application.port.`in`.command

import org.around.ieuri.auth.application.command.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.command.RegisterMemberCommand
import org.around.ieuri.auth.application.command.RequestPasswordResetCommand
import org.around.ieuri.auth.application.command.ResendVerificationCommand
import org.around.ieuri.auth.application.command.ResetPasswordCommand
import org.around.ieuri.auth.application.command.VerifyEmailCommand
import java.time.Instant
import java.util.UUID

/**
 * 회원가입 계열 유스케이스(쓰기). SNS 가입·이메일 검증·검증 재발송을 하나의 응집된 포트로 묶는다.
 * GraphQL 리졸버(인바운드 어댑터)가 이 인터페이스에만 의존한다.
 */
interface MemberRegistrationUseCase {
    /** SNS 회원가입. 가입은 이메일 검증 완료를 기다리지 않고 성공 처리된다. */
    fun registerBySns(command: RegisterMemberCommand): RegisterMemberResult

    /** 자체(EMAIL) 회원가입. 이메일 인증 전 미검증 상태로 시작하며 검증 코드를 발급한다. */
    fun registerByEmail(command: RegisterEmailMemberCommand): RegisterMemberResult

    /** 이메일 검증 코드 확인. */
    fun verifyEmail(command: VerifyEmailCommand): VerifyEmailResult

    /** 이메일 검증 코드 재발송. */
    fun resendVerification(command: ResendVerificationCommand): ResendVerificationResult

    /** 비밀번호 재설정 요청. 이메일로 재설정 코드를 발송한다(계정 유무는 드러내지 않음). */
    fun requestPasswordReset(command: RequestPasswordResetCommand)

    /** 비밀번호 재설정 실행. 코드 검증 성공 시 새 비밀번호로 교체하고 refresh 토큰을 폐기한다. */
    fun resetPassword(command: ResetPasswordCommand): ResetPasswordResult
}

/**
 * 가입 결과. 미검증이면 verificationPublicId(검증 핸들)가 채워진다.
 * member_id는 노출하지 않는다.
 */
data class RegisterMemberResult(
    val emailVerified: Boolean,
    val verificationPublicId: UUID?,
)

data class VerifyEmailResult(
    val verified: Boolean,
    /** 실패 사유(도메인 VerificationFailureReason 이름). 성공 시 null. */
    val failureReason: String?,
)

data class ResendVerificationResult(
    val verificationPublicId: UUID,
    val expiresAt: Instant,
)

/** 비밀번호 재설정 결과. 실패 사유는 코드 챌린지 실패(EXPIRED/CONSUMED/…) 또는 INVALID_OR_EXPIRED. */
data class ResetPasswordResult(
    val success: Boolean,
    val failureReason: String?,
)
