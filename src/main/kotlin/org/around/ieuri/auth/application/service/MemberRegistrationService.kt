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
import org.around.ieuri.auth.application.exception.AlreadyVerifiedException
import org.around.ieuri.auth.application.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.exception.NotEmailMemberException
import org.around.ieuri.auth.application.exception.ResendCooldownActiveException
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
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.PasswordResetRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsRepositoryPort
import org.around.ieuri.auth.application.port.out.security.EmailEncryptorPort
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

/** 회원가입 서비스: 가입·이메일 검증·재발송·인증 주소 재입력·비밀번호 재설정. 검증 코드는 이벤트로만 평문 전달한다(AFTER_COMMIT). */
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
    private val nicknameGenerator: NicknameGenerator,
    private val identifierProtector: LoginIdentifierProtectorPort,
    private val emailEncryptor: EmailEncryptorPort,
    private val providerTokenCollector: ProviderTokenCollector,
    private val emailUniqueness: ProviderEmailUniqueness,
    private val clock: Clock,
) : MemberRegistrationUseCase {

    /** 가입 시 닉네임 자동 생성(SPEC-0011 R20). 중복이 허용되므로 저장소를 묻지 않는다(R31). */
    private fun generateNickname(): Nickname = nicknameGenerator.generate()

    /**
     * 후보 중 처음으로 형식에 맞는 닉네임을 쓰고, 없으면 생성기로 물러난다(SPEC-0032 R29·R30).
     * - 잘라내지 않는다. 길이 규칙(2~20자)을 벗어나면 통째로 다음 후보로 넘어간다.
     */
    private fun nicknameFrom(vararg candidates: String?): Nickname = candidates.asSequence()
        .filterNotNull()
        // 형식을 벗어나면 오류 없이 다음 후보로 넘어간다(SPEC-0032 R30).
        .mapNotNull { runCatching { Nickname(it) }.getOrNull() }
        .firstOrNull()
        ?: generateNickname()

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
        // 클라이언트가 넘긴 값이 클레임보다 앞선다(SPEC-0032 R29).
        val nickname = nicknameFrom(command.nickname, identity.nickname)

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

        // 저장될 이메일(member.email)로 판정한다(R5). 제출값으로 보면 다른 주소를 검사하게 된다.
        emailUniqueness.ensureAvailable(command.provider, member.email)

        // 클라이언트가 R1(읽기 전용 표시)을 어긴 흔적. 저장값은 provider 값이므로 가입은 막지 않는다(R12).
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
            // 평문 코드는 이벤트로만 전달(AFTER_COMMIT 발송). 제출값이 아니라 저장된 주소로 보낸다(R12).
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
        val verification = loadRegistrationChallenge(command.verificationPublicId)

        val result = verification.verify(codeGenerator.hash(command.code), now)
        // 성공/실패 관계없이 attempts·consumed·verifiedAt 변경을 반영한다.
        verificationRepository.update(verification)

        return when (result) {
            is VerificationResult.Success -> {
                // 주소를 정정한 챌린지면 소유가 증명된 지금 회원의 이메일을 확정한다(R7-5).
                verification.targetEmailEncrypted?.let { applyRetargetedEmail(verification.memberId, it) }
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
        val existing = loadRegistrationChallenge(command.verificationPublicId)

        val memberId = existing.memberId
        if (!isResendCooldownPassed(memberId, now)) {
            throw VerificationRateLimitedException("잠시 후 다시 시도해 주세요.")
        }

        // 정정한 대기 주소를 이어받는다. 회원 행의 이메일을 읽으면 정정이 조용히 취소된다.
        val targetEmailEncrypted = existing.targetEmailEncrypted
        val toEmail = targetEmailEncrypted
            ?.let { Email(emailEncryptor.decrypt(it)) }
            ?: memberRepository.findEmailByMemberId(memberId)
            ?: throw VerificationNotFoundException("회원 정보를 찾을 수 없습니다.")

        // 가입 인증 목적의 활성 챌린지만 폐기한다. 진행 중인 이메일 변경 검증은 건드리지 않는다.
        verificationRepository.consumeActiveByMemberIdAndPurpose(memberId, VerificationPurpose.REGISTRATION, now)

        val verification = issueRegistrationChallenge(memberId, toEmail, now, targetEmailEncrypted)

        return ResendVerificationResult(
            verificationPublicId = verification.publicId,
            expiresAt = verification.expiresAt,
        )
    }

    /**
     * 인증 대기 주소를 정정한다(SPEC-0024 R7). provider를 가리지 않으며 인증을 마치면 닫힌다(R7-2).
     * - 회원의 이메일은 아직 바뀌지 않는다. 새 주소는 챌린지가 암호문으로 들고 있다가 `verifyEmail` 통과 시 반영된다
     *   — 즉시 반영하면 증명되지 않은 주소가 `(provider, email)` 유니크를 선점한다.
     * - 쿨다운은 `resendVerification`과 같은 창을 공유한다(R7-3). 나누면 주소를 바꿔가며 메일을 무제한 유발할 수 있다.
     */
    @Transactional
    override fun retargetEmailVerification(command: RetargetEmailVerificationCommand): RetargetEmailVerificationResult {
        val now = clock.instant()
        val newEmail = Email(command.newEmail)

        val member = memberRepository.load(command.memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

        // 인증 완료 회원은 INVALID_INPUT이 아니라 전용 코드로 거부한다(R7-2).
        if (member.emailVerified) {
            throw AlreadyVerifiedException("이미 이메일 인증을 마쳤습니다.")
        }
        member.ensureCanRetargetPendingEmail()

        emailUniqueness.ensureAvailable(member.provider, newEmail, excludingMemberId = command.memberId)
        // 창은 재발송과 공유하지만 코드는 RESEND_COOLDOWN_ACTIVE다(계약).
        if (!isResendCooldownPassed(command.memberId, now)) {
            throw ResendCooldownActiveException("잠시 후 다시 시도해 주세요.")
        }

        // 이전 주소로 발급된 가입 인증 코드를 무효화한다(R7-1). 다른 목적은 건드리지 않는다.
        verificationRepository.consumeActiveByMemberIdAndPurpose(
            command.memberId,
            VerificationPurpose.REGISTRATION,
            now,
        )
        // 대상 주소는 검증 통과 전에도 개인정보라 암호문으로만 저장한다(POL-012).
        val verification = issueRegistrationChallenge(
            memberId = command.memberId,
            toEmail = newEmail,
            now = now,
            targetEmailEncrypted = emailEncryptor.encrypt(newEmail.value),
        )

        // 주소 자체는 남기지 않는다(POL-013) — 무엇이 바뀌었는지는 회원 데이터가 갖는다.
        auditLog.record(
            memberId = command.memberId,
            eventType = "EMAIL_VERIFICATION_RETARGETED",
            payload = mapOf("provider" to member.provider.name),
        )

        return RetargetEmailVerificationResult(
            verificationPublicId = verification.publicId,
            expiresAt = verification.expiresAt,
        )
    }

    /**
     * 가입 인증 재발송 쿨다운이 지났는지(R7-3). 던지지 않고 판정만 돌려준다 — 호출부마다 오류 코드가 다르다.
     * - 재발송과 인증 주소 재입력이 한 창을 공유한다. 이메일 변경 검증은 이 창을 막지 않는다.
     */
    private fun isResendCooldownPassed(memberId: Long, now: java.time.Instant): Boolean {
        val lastCreatedAt = verificationRepository.findLatestCreatedAtByMemberIdAndPurpose(
            memberId,
            VerificationPurpose.REGISTRATION,
        )
        return lastCreatedAt == null || !lastCreatedAt.plus(verificationPolicy.resendCooldown).isAfter(now)
    }

    /**
     * 핸들을 가입 인증 챌린지로만 해석한다. 이메일 변경 핸들이면 없는 것과 같이 응답한다.
     * - 목적을 반드시 걸러야 한다. 이 경로는 인증이 없어, 거르지 않으면 남의 이메일 변경이 여기서 확정된다.
     */
    private fun loadRegistrationChallenge(publicId: UUID): EmailVerification = verificationRepository.findByPublicId(publicId)
        ?.takeIf { it.purpose == VerificationPurpose.REGISTRATION }
        ?: throw VerificationNotFoundException("유효하지 않은 검증 요청입니다.")

    /**
     * 가입 인증 코드를 발급하고 평문 코드를 이벤트로만 전달한다(AFTER_COMMIT 발송).
     * - [targetEmailEncrypted]는 주소 정정(R7)에서만 채워지고, 최초 가입·단순 재발송은 null이다.
     * - 호출자는 먼저 같은 목적의 활성 챌린지를 폐기해야 한다. 둘이 살아 있으면 확정될 주소가 정해지지 않는다.
     */
    private fun issueRegistrationChallenge(
        memberId: Long,
        toEmail: Email,
        now: java.time.Instant,
        targetEmailEncrypted: String? = null,
    ): EmailVerification {
        val plainCode = codeGenerator.generate()
        val verification = EmailVerification.issue(
            publicId = UUID.randomUUID(),
            memberId = memberId,
            codeHash = codeGenerator.hash(plainCode),
            ttl = verificationPolicy.ttl,
            now = now,
            maxAttempts = verificationPolicy.maxAttempts,
            targetEmailEncrypted = targetEmailEncrypted,
        )
        verificationRepository.save(verification)
        eventPublisher.publish(VerificationEmailRequested(toEmail.value, plainCode))
        return verification
    }

    /** 정정된 주소를 회원에게 확정한다(R7-5). EMAIL 회원은 로그인 식별자도 함께 바꾸고, SNS는 건드리지 않는다. */
    private fun applyRetargetedEmail(memberId: Long, targetEmailEncrypted: String) {
        val member = memberRepository.load(memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        val newEmail = Email(emailEncryptor.decrypt(targetEmailEncrypted))
        // 발급 후 확정 사이에 다른 회원이 그 주소를 가져갔을 수 있다.
        emailUniqueness.ensureAvailable(member.provider, newEmail, excludingMemberId = memberId)

        val protectedIdentifier =
            if (member.provider == Provider.EMAIL) identifierProtector.protect(newEmail.value) else null
        memberRepository.updateEmail(
            memberId = memberId,
            newEmail = newEmail,
            emailIdentifier = emailUniqueness.identifierOf(newEmail),
            protectedIdentifier = protectedIdentifier,
        )
    }

    /** 비밀번호 재설정 요청. 가입되지 않은 주소는 MEMBER_NOT_FOUND, SNS 계정은 NOT_EMAIL_MEMBER로 거부한다. */
    @Transactional
    override fun requestPasswordReset(command: RequestPasswordResetCommand) {
        val now = clock.instant()
        val email = Email(command.email).value
        val auth = memberRepository.findEmailAuthentication(identifierProtector.protect(email))
            ?: throw MemberNotFoundException("가입되지 않은 이메일입니다.")
        if (auth.passwordHash == null) {
            throw NotEmailMemberException("SNS로 가입한 계정입니다. 해당 SNS로 로그인해 주세요.")
        }

        // 발송 쿨다운. 없으면 주소를 아는 제3자가 특정인의 메일함에 코드를 무제한 투하할 수 있다.
        val lastRequestedAt = passwordResetRepository.findLatestCreatedAtByMemberId(auth.memberId)
        if (lastRequestedAt != null && lastRequestedAt.plus(verificationPolicy.resendCooldown).isAfter(now)) {
            throw ResendCooldownActiveException("잠시 후 다시 시도해 주세요.")
        }

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
        if (auth?.passwordHash == null) return ResetPasswordResult(success = false, failureReason = CODE_MISMATCH)
        val reset = passwordResetRepository.findActiveByMemberId(auth.memberId)
            ?: return ResetPasswordResult(success = false, failureReason = CODE_MISMATCH)

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

    /** 활성 약관 전체의 동의/비동의 목록과 필수 약관 코드 집합을 만든다. SNS·EMAIL 가입이 공유한다. */
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
        /**
         * 계정 없음·SNS 계정·챌린지 없음·코드 불일치가 모두 이 값을 낸다. 사유가 갈리면 인증 없이 계정을 열거할 수 있다.
         * - 만료·소진은 실제 챌린지가 있을 때만 나오므로 그대로 둔다.
         */
        const val CODE_MISMATCH = "CODE_MISMATCH"

        val log = LoggerFactory.getLogger(MemberRegistrationService::class.java)!!
    }
}
