package org.around.ieuri.auth.application.port.`in`.command

import org.around.ieuri.auth.application.command.RegisterMemberCommand
import org.around.ieuri.auth.application.command.ResendVerificationCommand
import org.around.ieuri.auth.application.command.VerifyEmailCommand
import java.time.Instant
import java.util.UUID

/**
 * 인바운드(쓰기) 유스케이스 포트. GraphQL 리졸버(인바운드 어댑터)가 이 인터페이스에만 의존한다.
 */
interface RegisterMemberUseCase {
    fun handle(command: RegisterMemberCommand): RegisterMemberResult
}

interface VerifyEmailUseCase {
    fun handle(command: VerifyEmailCommand): VerifyEmailResult
}

interface ResendVerificationUseCase {
    fun handle(command: ResendVerificationCommand): ResendVerificationResult
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
