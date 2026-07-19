package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.command.AgreementInput
import org.around.ieuri.auth.application.command.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.command.RegisterMemberCommand
import org.around.ieuri.auth.application.command.RequestPasswordResetCommand
import org.around.ieuri.auth.application.command.ResendVerificationCommand
import org.around.ieuri.auth.application.command.ResetPasswordCommand
import org.around.ieuri.auth.application.command.VerifyEmailCommand
import org.around.ieuri.auth.application.event.PasswordResetRequested
import org.around.ieuri.auth.application.event.VerificationEmailRequested
import org.around.ieuri.auth.application.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.exception.VerificationRateLimitedException
import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.around.ieuri.auth.application.port.`in`.command.MemberRegistrationUseCase
import org.around.ieuri.auth.application.port.`in`.command.RegisterMemberResult
import org.around.ieuri.auth.application.port.`in`.command.ResendVerificationResult
import org.around.ieuri.auth.application.port.`in`.command.ResetPasswordResult
import org.around.ieuri.auth.application.port.`in`.command.VerifyEmailResult
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.PasswordResetRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsRepositoryPort
import org.around.ieuri.auth.application.port.out.security.PasswordEncoderPort
import org.around.ieuri.auth.application.port.out.security.VerificationCodeGeneratorPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.aggregate.PasswordReset
import org.around.ieuri.auth.domain.aggregate.Role
import org.around.ieuri.auth.domain.aggregate.VerificationResult
import org.around.ieuri.auth.domain.event.EmailVerified
import org.around.ieuri.auth.domain.event.MemberRegistered
import org.around.ieuri.auth.domain.vo.Agreement
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.PasswordHash
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * 회원가입 계열 서비스. SNS 가입·이메일 검증·검증 재발송을 한 서비스로 묶어 관리한다.
 * 가입은 이메일 검증 완료를 기다리지 않고 성공 처리하며, 검증 코드는 이벤트로만 평문 전달한다(AFTER_COMMIT).
 */
@Service
class MemberRegistrationService(
    private val snsVerifier: SnsIdentityVerifierPort,
    private val memberRepository: MemberRepositoryPort,
    private val roleRepository: RoleRepositoryPort,
    private val termsRepository: TermsRepositoryPort,
    private val verificationRepository: EmailVerificationRepositoryPort,
    private val passwordResetRepository: PasswordResetRepositoryPort,
    private val refreshTokenStore: RefreshTokenStorePort,
    private val codeGenerator: VerificationCodeGeneratorPort,
    private val auditLog: AuditLogPort,
    private val eventPublisher: DomainEventPublisherPort,
    private val verificationPolicy: VerificationPolicy,
    private val passwordEncoder: PasswordEncoderPort,
    private val clock: Clock,
) : MemberRegistrationUseCase {

    @Transactional
    override fun registerBySns(command: RegisterMemberCommand): RegisterMemberResult {
        val now = clock.instant()

        val identity = snsVerifier.verify(command.provider, command.idToken)
        val providerUserId = ProviderUserId(identity.providerUserId)

        if (memberRepository.existsByProviderAndProviderUserId(command.provider, providerUserId)) {
            throw MemberAlreadyRegisteredException("이미 가입된 계정입니다.")
        }

        val submittedEmail = Email(command.submittedEmail)
        val providerEmail = identity.email?.let { Email(it) }
        val nickname = Nickname(identity.nickname?.takeIf { it.isNotBlank() } ?: "여행자")

        val (agreements, requiredCodes) = buildAgreements(command.agreements, now)

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

        // 기본 역할(USER) 부여. 관리 역할은 부트스트랩/역할 부여로만 얻는다.
        roleRepository.assignRoleToMember(memberId, Role.USER)

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

        return RegisterMemberResult(
            emailVerified = member.emailVerified,
            verificationPublicId = verificationPublicId,
        )
    }

    @Transactional
    override fun registerByEmail(command: RegisterEmailMemberCommand): RegisterMemberResult {
        val now = clock.instant()
        val email = Email(command.email)
        val providerUserId = ProviderUserId(email.value)

        if (memberRepository.existsByProviderAndProviderUserId(Provider.EMAIL, providerUserId)) {
            throw MemberAlreadyRegisteredException("이미 가입된 이메일입니다.")
        }

        val (agreements, requiredCodes) = buildAgreements(command.agreements, now)
        val passwordHash = PasswordHash(passwordEncoder.encode(command.password))
        val nickname = Nickname(email.value.substringBefore('@').take(NICKNAME_MAX_LENGTH))

        val member = Member.registerByEmail(
            email = email,
            passwordHash = passwordHash,
            nickname = nickname,
            ageVerified = command.ageVerified,
            agreements = agreements,
            requiredTermsCodes = requiredCodes,
            now = now,
        )

        val memberId = memberRepository.save(member)
        roleRepository.assignRoleToMember(memberId, Role.USER)

        // EMAIL 가입은 항상 미검증으로 시작 → 검증 코드 발급(평문은 이벤트로만 전달, AFTER_COMMIT 발송).
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
        eventPublisher.publish(VerificationEmailRequested(email.value, plainCode))

        auditLog.record(
            memberId = memberId,
            eventType = "MEMBER_REGISTERED",
            payload = mapOf("provider" to Provider.EMAIL.name, "emailVerified" to false),
        )

        return RegisterMemberResult(emailVerified = false, verificationPublicId = verification.publicId)
    }

    @Transactional
    override fun verifyEmail(command: VerifyEmailCommand): VerifyEmailResult {
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

    @Transactional
    override fun resendVerification(command: ResendVerificationCommand): ResendVerificationResult {
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

    @Transactional
    override fun requestPasswordReset(command: RequestPasswordResetCommand) {
        val now = clock.instant()
        val email = Email(command.email).value
        val auth = memberRepository.findEmailAuthentication(email)
        // 계정 열거 방지: 계정이 없거나 비밀번호가 없는(SNS) 계정이면 아무 일도 하지 않고 조용히 성공한다.
        if (auth?.passwordHash == null) return

        passwordResetRepository.consumeActiveByMemberId(auth.memberId)
        val plainCode = codeGenerator.generate()
        val reset = PasswordReset.issue(
            publicId = UUID.randomUUID(),
            memberId = auth.memberId,
            codeHash = codeGenerator.hash(plainCode),
            ttl = verificationPolicy.ttl,
            now = now,
            maxAttempts = verificationPolicy.maxAttempts,
        )
        passwordResetRepository.save(reset)
        eventPublisher.publish(PasswordResetRequested(email, plainCode))
        auditLog.record(auth.memberId, "PASSWORD_RESET_REQUESTED", emptyMap())
    }

    @Transactional
    override fun resetPassword(command: ResetPasswordCommand): ResetPasswordResult {
        val now = clock.instant()
        val email = Email(command.email).value
        val auth = memberRepository.findEmailAuthentication(email)
        // 계정 없음/비밀번호 없음/활성 챌린지 없음은 모두 동일하게 처리(계정 열거 방지).
        if (auth?.passwordHash == null) return ResetPasswordResult(success = false, failureReason = "INVALID_OR_EXPIRED")
        val reset = passwordResetRepository.findActiveByMemberId(auth.memberId)
            ?: return ResetPasswordResult(success = false, failureReason = "INVALID_OR_EXPIRED")

        val result = reset.verify(codeGenerator.hash(command.code), now)
        // 성공/실패 관계없이 attempts·consumed 변경을 반영한다.
        passwordResetRepository.update(reset)

        return when (result) {
            is VerificationResult.Success -> {
                memberRepository.updatePassword(auth.memberId, passwordEncoder.encode(command.newPassword))
                // 비밀번호 변경 → 기존 세션 무효화(refresh 전량 폐기).
                refreshTokenStore.revokeAllByMember(auth.memberId)
                auditLog.record(auth.memberId, "PASSWORD_RESET", emptyMap())
                ResetPasswordResult(success = true, failureReason = null)
            }
            is VerificationResult.Failure -> ResetPasswordResult(success = false, failureReason = result.reason.name)
        }
    }

    /**
     * 활성 약관 전체에 대해 동의/비동의 이력(append-only)을 만들고 필수 약관 코드 집합을 함께 반환한다.
     * SNS·EMAIL 가입이 공유한다.
     */
    private fun buildAgreements(inputs: List<AgreementInput>, now: java.time.Instant): AgreementBuild {
        val activeTerms = termsRepository.findActiveTerms(now)
        val requiredCodes = activeTerms.filter { it.required }.map { it.code }.toSet()
        val agreedByCode = inputs.associate { it.termsCode to it.agreed }
        val agreements = activeTerms.map { terms ->
            Agreement(
                termsId = terms.id,
                termsCode = terms.code,
                termsVersion = terms.version,
                agreed = agreedByCode[terms.code] ?: false,
                agreedAt = now,
            )
        }
        return AgreementBuild(agreements, requiredCodes)
    }

    private data class AgreementBuild(val agreements: List<Agreement>, val requiredCodes: Set<String>)

    private companion object {
        const val NICKNAME_MAX_LENGTH = 50
    }
}
