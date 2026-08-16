package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.command.AgreementInput
import org.around.ieuri.auth.application.command.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.command.RegisterMemberCommand
import org.around.ieuri.auth.application.command.RequestPasswordResetCommand
import org.around.ieuri.auth.application.command.ResendVerificationCommand
import org.around.ieuri.auth.application.command.ResetPasswordCommand
import org.around.ieuri.auth.application.command.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.command.VerifyEmailCommand
import org.around.ieuri.auth.application.event.PasswordResetRequested
import org.around.ieuri.auth.application.event.VerificationEmailRequested
import org.around.ieuri.auth.application.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.exception.VerificationRateLimitedException
import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.around.ieuri.auth.application.port.`in`.command.MemberRegistrationUseCase
import org.around.ieuri.auth.application.port.`in`.command.RegisterMemberResult
import org.around.ieuri.auth.application.port.`in`.command.ResendVerificationResult
import org.around.ieuri.auth.application.port.`in`.command.ResetPasswordResult
import org.around.ieuri.auth.application.port.`in`.command.RetargetEmailVerificationResult
import org.around.ieuri.auth.application.port.`in`.command.VerifyEmailResult
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberProfileRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.PasswordResetRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsRepositoryPort
import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
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
import org.around.ieuri.auth.domain.strategy.NicknameGenerator
import org.around.ieuri.auth.domain.vo.Agreement
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.PasswordHash
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.around.ieuri.auth.domain.vo.VerificationPurpose
import org.around.ieuri.common.logging.maskEmail
import org.slf4j.LoggerFactory
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
    private val profileRepository: MemberProfileRepositoryPort,
    private val nicknameGenerator: NicknameGenerator,
    private val identifierProtector: LoginIdentifierProtectorPort,
    private val providerTokenCollector: ProviderTokenCollector,
    private val emailUniqueness: ProviderEmailUniqueness,
    private val clock: Clock,
) : MemberRegistrationUseCase {

    /**
     * 가입 시 닉네임 자동 생성(SPEC-0011 R20). 이미 쓰이는 값이면 생성기가 다시 뽑는다.
     *
     * 회원이 아직 없어 "본인 제외"의 기준이 없으므로 [NEW_MEMBER_ID]로 조회한다 —
     * 존재하지 않는 id라 사실상 전체 대상 조회다.
     */
    private fun generateNickname(): Nickname =
        nicknameGenerator.generate { candidate ->
            profileRepository.existsByNicknameExcludingMember(candidate, NEW_MEMBER_ID)
        }

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
        // provider가 준 값을 쓰지 않는다(SPEC-0011 R20, POL-006 개정) — 실명이 그대로
        // 노출되거나 같은 값이 여러 회원에게 들어와 고유성을 깨뜨린다.
        val nickname = generateNickname()

        val (agreements, requiredCodes) = buildAgreements(command.agreements, now)

        val member = Member.register(
            provider = command.provider,
            providerUserId = providerUserId,
            providerEmail = providerEmail,
            fallbackEmail = submittedEmail,
            nickname = nickname,
            profileImageUrl = identity.profileImageUrl,
            ageVerified = command.ageVerified,
            agreements = agreements,
            requiredTermsCodes = requiredCodes,
            now = now,
        )

        // 저장 이메일이 확정된 뒤에 판정한다(R5) — provider가 준 값을 쓰는 경우
        // 제출값으로 조회하면 실제로 저장될 주소와 다른 주소를 검사하게 된다.
        emailUniqueness.ensureAvailable(command.provider, member.email)

        // 클라이언트가 R1(읽기 전용 표시)을 어긴 흔적. 가입을 막지는 않는다 — R12가 제출값을
        // 이미 버렸으므로 저장값은 안전하고, 여기서 거부하면 클라이언트 버그가 가입 장애가 된다.
        if (providerEmail != null && providerEmail != submittedEmail) {
            log.warn(
                "SNS 가입: 제출 이메일이 provider 값과 다르다. provider={} submitted={} stored={}",
                command.provider.name,
                maskEmail(submittedEmail.value),
                maskEmail(providerEmail.value),
            )
        }

        val memberId = memberRepository.save(member)

        // 기본 역할(USER) 부여. 관리 역할은 부트스트랩/역할 부여로만 얻는다.
        roleRepository.assignRoleToMember(memberId, Role.USER)

        // 탈퇴 시 연결 해제용 토큰 확보. 실패해도 가입은 성공한다(R7).
        providerTokenCollector.collect(memberId, command.provider, command.authorizationCode)

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
            // 제출값이 아니라 **저장된 주소**로 보낸다 — 둘은 R12에 의해 다를 수 있다.
            eventPublisher.publish(VerificationEmailRequested(member.email.value, plainCode))
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
        val providerUserId = ProviderUserId(identifierProtector.protect(email.value))

        if (memberRepository.existsByProviderAndProviderUserId(Provider.EMAIL, providerUserId)) {
            throw MemberAlreadyRegisteredException("이미 가입된 이메일입니다.")
        }
        emailUniqueness.ensureAvailable(Provider.EMAIL, email)

        val (agreements, requiredCodes) = buildAgreements(command.agreements, now)
        val passwordHash = PasswordHash(passwordEncoder.encode(command.password))
        // 이메일 로컬파트를 쓰지 않는다(R20) — 주소 일부가 표시명으로 새어 나간다.
        val nickname = generateNickname()

        val member = Member.registerByEmail(
            email = email,
            providerUserId = providerUserId,
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
        ensureResendCooldownPassed(memberId, now)

        // 이전 활성 챌린지 폐기 후 새 코드 발급. **가입 인증 목적만** 폐기한다 —
        // 같은 회원이 이메일 변경 검증을 동시에 대기 중일 수 있다.
        verificationRepository.consumeActiveByMemberIdAndPurpose(memberId, VerificationPurpose.REGISTRATION, now)

        val email = memberRepository.findEmailByMemberId(memberId)
            ?: throw VerificationNotFoundException("회원 정보를 찾을 수 없습니다.")
        val verification = issueRegistrationChallenge(memberId, email, now)

        return ResendVerificationResult(
            verificationPublicId = verification.publicId,
            expiresAt = verification.expiresAt,
        )
    }

    /**
     * 인증 대기 주소를 정정한다(SPEC-0024 R7). 이메일 가입자와 provider가 이메일을 주지 않은
     * SNS 가입자(R3)가 함께 쓴다.
     *
     * **`changeEmail`과 다른 유스케이스다** — 그쪽은 이미 인증된 주소를 바꾸는 것이라
     * `Provider.EMAIL` 전용이고(R6), 이쪽은 아직 인증되지 않은 대기 주소를 고치는 것이라
     * provider를 가리지 않는다. 인증을 마치면 이 경로는 닫힌다(R7-2).
     *
     * 쿨다운은 `resendVerification`과 **같은 창을 공유한다**(R7-3). 별도 창을 주면 주소를
     * 바꿔가며 메일 발송을 무제한으로 유발할 수 있다.
     */
    @Transactional
    override fun retargetEmailVerification(command: RetargetEmailVerificationCommand): RetargetEmailVerificationResult {
        val now = clock.instant()
        val newEmail = Email(command.newEmail)

        val member = memberRepository.load(command.memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

        // 인증 완료 회원의 호출은 도메인이 거부한다(R7-2). 정정된 회원은 인증 상태가 그대로 거짓이다.
        val retargeted = member.retargetPendingEmail(newEmail)

        emailUniqueness.ensureAvailable(member.provider, newEmail, excludingMemberId = command.memberId)
        ensureResendCooldownPassed(command.memberId, now)

        // `Provider.EMAIL` 회원은 provider_user_id가 곧 이메일의 HMAC이라 함께 바뀌어야
        // 다음 로그인이 성립한다(SPEC-0011 R7·R8). SNS는 provider가 준 값이라 건드리지 않는다.
        val protectedIdentifier =
            if (member.provider == Provider.EMAIL) identifierProtector.protect(newEmail.value) else null
        memberRepository.updateEmail(
            memberId = command.memberId,
            newEmail = retargeted.email,
            emailIdentifier = emailUniqueness.identifierOf(newEmail),
            protectedIdentifier = protectedIdentifier,
        )

        // 이전 주소로 발급된 가입 인증 코드를 무효화한다(R7-1). 다른 목적은 건드리지 않는다.
        verificationRepository.consumeActiveByMemberIdAndPurpose(
            command.memberId,
            VerificationPurpose.REGISTRATION,
            now,
        )
        val verification = issueRegistrationChallenge(command.memberId, newEmail, now)

        // 주소 자체는 남기지 않는다(POL-013) — 무엇이 바뀌었는지는 회원 데이터가 갖는다.
        auditLog.record(
            memberId = command.memberId,
            eventType = "EMAIL_VERIFICATION_RETARGETED",
            payload = mapOf(
                "provider" to member.provider.name,
                "loginIdentifierUpdated" to (protectedIdentifier != null),
            ),
        )

        return RetargetEmailVerificationResult(
            verificationPublicId = verification.publicId,
            expiresAt = verification.expiresAt,
        )
    }

    /**
     * 가입 인증 재발송 쿨다운(R7-3). `resendVerification`과 인증 주소 재입력이 한 창을 공유한다.
     * 용도별로 세므로 진행 중인 이메일 변경 검증이 이 창을 막지 않는다.
     */
    private fun ensureResendCooldownPassed(memberId: Long, now: java.time.Instant) {
        val lastCreatedAt = verificationRepository.findLatestCreatedAtByMemberIdAndPurpose(
            memberId,
            VerificationPurpose.REGISTRATION,
        )
        if (lastCreatedAt != null && lastCreatedAt.plus(verificationPolicy.resendCooldown).isAfter(now)) {
            throw VerificationRateLimitedException("잠시 후 다시 시도해 주세요.")
        }
    }

    /** 가입 인증 코드를 발급하고 평문 코드를 이벤트로만 전달한다(AFTER_COMMIT 발송). */
    private fun issueRegistrationChallenge(
        memberId: Long,
        toEmail: Email,
        now: java.time.Instant,
    ): EmailVerification {
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
        eventPublisher.publish(VerificationEmailRequested(toEmail.value, plainCode))
        return verification
    }

    @Transactional
    override fun requestPasswordReset(command: RequestPasswordResetCommand) {
        val now = clock.instant()
        val email = Email(command.email).value
        val auth = memberRepository.findEmailAuthentication(identifierProtector.protect(email))
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
        val auth = memberRepository.findEmailAuthentication(identifierProtector.protect(email))
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
        /** 아직 회원이 없어 '본인 제외'의 기준이 없을 때 쓰는 자리값. 존재하지 않는 id다. */
        const val NEW_MEMBER_ID = 0L

        val log = LoggerFactory.getLogger(MemberRegistrationService::class.java)!!
    }
}
