package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.command.VerifyEmailCommand
import org.around.ieuri.auth.application.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.port.`in`.command.VerifyEmailResult
import org.around.ieuri.auth.application.port.`in`.command.VerifyEmailUseCase
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.aggregate.VerificationResult
import org.around.ieuri.auth.domain.event.EmailVerified
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 이메일 검증 코드 확인. publicId(핸들) + 코드로 활성 챌린지를 찾아 도메인 규칙(EmailVerification.verify)에
 * 위임한다. 실패해도 attempts 증가를 커밋하고, 성공 시 member.email_verified를 dirty-check로 갱신한다.
 */
@Service
class VerifyEmailHandler(
    private val verificationRepository: EmailVerificationRepositoryPort,
    private val memberRepository: MemberRepositoryPort,
    private val codeGenerator: VerificationCodeGeneratorPort,
    private val auditLog: AuditLogPort,
    private val eventPublisher: DomainEventPublisherPort,
    private val clock: Clock,
) : VerifyEmailUseCase {

    @Transactional
    override fun handle(command: VerifyEmailCommand): VerifyEmailResult {
        val now = clock.instant()
        val verification = verificationRepository.findByPublicId(command.verificationPublicId)
            ?: throw VerificationNotFoundException("유효하지 않은 검증 요청입니다.")

        val result = verification.verify(codeGenerator.hash(command.code), now)
        // 성공/실패 관계없이 attempts·consumed·verifiedAt 변경을 반영한다.
        verificationRepository.update(verification)

        return when (result) {
            is VerificationResult.Success -> {
                memberRepository.markEmailVerified(verification.memberId, now)
                auditLog.record(
                    memberId = verification.memberId,
                    eventType = "EMAIL_VERIFIED",
                    payload = mapOf("publicId" to verification.publicId.toString()),
                )
                eventPublisher.publish(EmailVerified(verification.memberId, now))
                VerifyEmailResult(verified = true, failureReason = null)
            }
            is VerificationResult.Failure -> VerifyEmailResult(verified = false, failureReason = result.reason.name)
        }
    }
}
