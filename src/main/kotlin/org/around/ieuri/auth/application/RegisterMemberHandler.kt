package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.command.RegisterMemberCommand
import org.around.ieuri.auth.application.event.VerificationEmailRequested
import org.around.ieuri.auth.application.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.around.ieuri.auth.application.port.`in`.command.RegisterMemberResult
import org.around.ieuri.auth.application.port.`in`.command.RegisterMemberUseCase
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsRepositoryPort
import org.around.ieuri.auth.application.port.out.security.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.event.MemberRegistered
import org.around.ieuri.auth.domain.vo.Agreement
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * 회원가입 오케스트레이션. 한 트랜잭션에서:
 *   SNS 검증 → 중복 확인 → 도메인 불변식(Member.register) → 회원/프로필/동의이력 저장 →
 *   이메일 검증 여부 판정 → (미검증 시) 검증 코드 발급 → 감사 이력 기록 → 이벤트 발행.
 * 가입은 이메일 검증 완료를 기다리지 않고 성공 처리한다.
 */
@Service
class RegisterMemberHandler(
    private val snsVerifier: SnsIdentityVerifierPort,
    private val memberRepository: MemberRepositoryPort,
    private val termsRepository: TermsRepositoryPort,
    private val verificationRepository: EmailVerificationRepositoryPort,
    private val codeGenerator: VerificationCodeGeneratorPort,
    private val auditLog: AuditLogPort,
    private val eventPublisher: DomainEventPublisherPort,
    private val verificationPolicy: VerificationPolicy,
    private val clock: Clock,
) : RegisterMemberUseCase {

    @Transactional
    override fun handle(command: RegisterMemberCommand): RegisterMemberResult {
        val now = clock.instant()

        val identity = snsVerifier.verify(command.provider, command.idToken)
        val providerUserId = ProviderUserId(identity.providerUserId)

        if (memberRepository.existsByProviderAndProviderUserId(command.provider, providerUserId)) {
            throw MemberAlreadyRegisteredException("이미 가입된 계정입니다.")
        }

        val submittedEmail = Email(command.submittedEmail)
        val providerEmail = identity.email?.let { Email(it) }
        val nickname = Nickname(identity.nickname?.takeIf { it.isNotBlank() } ?: "여행자")

        val activeTerms = termsRepository.findActiveTerms(now)
        val requiredCodes = activeTerms.filter { it.required }.map { it.code }.toSet()
        val agreedByCode = command.agreements.associate { it.termsCode to it.agreed }
        // 활성 약관 전체에 대해 동의/비동의 이력을 남긴다(append-only).
        val agreements = activeTerms.map { terms ->
            Agreement(
                termsId = terms.id,
                termsCode = terms.code,
                termsVersion = terms.version,
                agreed = agreedByCode[terms.code] ?: false,
                agreedAt = now,
            )
        }

        val member = Member.register(
            provider = command.provider,
            providerUserId = providerUserId,
            submittedEmail = submittedEmail,
            providerEmail = providerEmail,
            providerEmailVerified = identity.emailVerified,
            nickname = nickname,
            profileImageUrl = identity.profileImageUrl,
            ageVerified = command.ageVerified,
            agreements = agreements,
            requiredTermsCodes = requiredCodes,
            now = now,
        )

        val memberId = memberRepository.save(member)

        var verificationPublicId: UUID? = null
        if (!member.emailVerified) {
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
            verificationPublicId = verification.publicId
            // 평문 코드는 저장하지 않고 이벤트로만 전달 → AFTER_COMMIT 발송.
            eventPublisher.publish(VerificationEmailRequested(submittedEmail.value, plainCode))
        }

        auditLog.record(
            memberId = memberId,
            eventType = "MEMBER_REGISTERED",
            payload = mapOf(
                "provider" to command.provider.name,
                "emailVerified" to member.emailVerified,
            ),
        )
        eventPublisher.publish(
            MemberRegistered(
                memberId = memberId,
                provider = command.provider,
                emailVerified = member.emailVerified,
                verificationPublicId = verificationPublicId,
                occurredAt = now,
            ),
        )

        return RegisterMemberResult(
            emailVerified = member.emailVerified,
            verificationPublicId = verificationPublicId,
        )
    }
}
