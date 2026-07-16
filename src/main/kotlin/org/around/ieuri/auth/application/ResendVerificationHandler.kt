package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.command.ResendVerificationCommand
import org.around.ieuri.auth.application.event.VerificationEmailRequested
import org.around.ieuri.auth.application.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.exception.VerificationRateLimitedException
import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.around.ieuri.auth.application.port.`in`.command.ResendVerificationResult
import org.around.ieuri.auth.application.port.`in`.command.ResendVerificationUseCase
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.time.Clock

/**
 * 검증 코드 재발송. 재발송 rate limit(쿨다운)을 강제하고, 이전 활성 챌린지를 폐기한 뒤 새 코드를 발급한다.
 * 회원 이메일은 암호문으로 저장되어 있으므로 포트가 복호화해 반환한다.
 */
@Service
class ResendVerificationHandler(
    private val verificationRepository: EmailVerificationRepositoryPort,
    private val memberRepository: MemberRepositoryPort,
    private val codeGenerator: VerificationCodeGeneratorPort,
    private val eventPublisher: DomainEventPublisherPort,
    private val verificationPolicy: VerificationPolicy,
    private val clock: Clock,
) : ResendVerificationUseCase {

    @Transactional
    override fun handle(command: ResendVerificationCommand): ResendVerificationResult {
        val now = clock.instant()
        val existing = verificationRepository.findByPublicId(command.verificationPublicId)
            ?: throw VerificationNotFoundException("유효하지 않은 검증 요청입니다.")

        val memberId = existing.memberId
        val lastCreatedAt = verificationRepository.findLatestCreatedAtByMemberId(memberId)
        if (lastCreatedAt != null && lastCreatedAt.plus(verificationPolicy.resendCooldown).isAfter(now)) {
            throw VerificationRateLimitedException("잠시 후 다시 시도해 주세요.")
        }

        // 이전 활성 챌린지 폐기 후 새 코드 발급.
        verificationRepository.consumeActiveByMemberId(memberId, now)
        val plainCode = codeGenerator.generate()
        val verification = EmailVerification.issue(
            publicId = UUID.randomUUID(),
            memberId = memberId,
            codeHash = codeGenerator.hash(plainCode),
            ttl = verificationPolicy.ttl,
            now = now,
            maxAttempts = verificationPolicy.maxAttempts,
        )
        verificationRepository.save(verification)

        val email = memberRepository.findEmailByMemberId(memberId)
            ?: throw VerificationNotFoundException("회원 정보를 찾을 수 없습니다.")
        eventPublisher.publish(VerificationEmailRequested(email.value, plainCode))

        return ResendVerificationResult(
            verificationPublicId = verification.publicId,
            expiresAt = verification.expiresAt,
        )
    }
}
