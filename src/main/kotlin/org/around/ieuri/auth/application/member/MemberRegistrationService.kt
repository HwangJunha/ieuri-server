package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberRegistrationUseCase
import org.around.ieuri.auth.application.member.provided.dto.AgreementInput
import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberResult
import org.around.ieuri.auth.application.member.provided.dto.RegisterSnsIdentityCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationResult
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationResult
import org.around.ieuri.auth.application.member.provided.dto.SnsRegistrationResult
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailResult
import org.around.ieuri.auth.application.member.provided.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.provided.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.member.required.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.application.member.required.PasswordEncoderPort
import org.around.ieuri.auth.application.member.required.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.member.ActiveTerms
import org.around.ieuri.auth.domain.member.Agreement
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.EmailVerification
import org.around.ieuri.auth.domain.member.EmailVerificationIssue
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.MemberRoleChange
import org.around.ieuri.auth.domain.member.Nickname
import org.around.ieuri.auth.domain.member.NicknameGenerator
import org.around.ieuri.auth.domain.member.PasswordHash
import org.around.ieuri.auth.domain.member.Profile
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.auth.domain.member.Role
import org.around.ieuri.auth.domain.member.VerificationCode
import org.around.ieuri.auth.domain.member.VerificationPolicy
import org.around.ieuri.auth.domain.member.VerificationPurpose
import org.around.ieuri.auth.domain.member.VerificationResult
import org.around.ieuri.auth.domain.member.event.EmailVerificationRetargeted
import org.around.ieuri.auth.domain.member.event.EmailVerified
import org.around.ieuri.auth.domain.member.event.MemberRegistered
import org.around.ieuri.common.logging.maskEmail
import org.around.ieuri.common.persistence.ExposedConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** 가입과 가입 이메일 인증(확인·재발송·주소 정정). 평문 코드는 이벤트로만 나가고 커밋 뒤에 발송된다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class MemberRegistrationService(
    private val memberRepository: MemberRepository,
    private val codeGenerator: VerificationCodeGeneratorPort,
    private val eventPublisher: MemberEventPublisherPort,
    private val verificationPolicy: VerificationPolicy,
    private val passwordEncoder: PasswordEncoderPort,
    private val nicknameGenerator: NicknameGenerator,
    private val identifierProtector: LoginIdentifierProtectorPort,
    private val emailUniqueness: ProviderEmailUniqueness,
    private val clock: Clock,
) : MemberRegistrationUseCase {

    override fun registerBySnsIdentity(command: RegisterSnsIdentityCommand): SnsRegistrationResult {
        val now = clock.instant()
        val request = command.request
        val providerUserId = ProviderUserId(command.providerUserId)
        if (memberRepository.existsByProviderUserId(request.provider, providerUserId)) {
            throw MemberAlreadyRegisteredException("이미 가입된 계정입니다.")
        }

        val submittedEmail = Email(request.submittedEmail)
        val providerEmail = command.email?.let { Email(it) }
        // 클라이언트가 넘긴 값이 클레임보다 앞선다(SPEC-0032 R29).
        val nickname = nicknameFrom(request.nickname, command.nickname)
        val active = activeTerms(now)
        val agreements = agreementsOf(request.agreements, active, now)

        val member = Member.register(
            provider = request.provider,
            providerUserId = providerUserId,
            providerEmail = providerEmail,
            fallbackEmail = submittedEmail,
            ageVerified = request.ageVerified,
            agreements = agreements,
            requiredTermsCodes = active.requiredCodes,
        )
        // 저장될 이메일로 판정한다(R5). 제출값으로 보면 다른 주소를 검사하게 된다.
        emailUniqueness.ensureAvailable(request.provider, member.email)

        // 클라이언트가 R1(읽기 전용 표시)을 어긴 흔적. 저장값은 provider 값이므로 가입은 막지 않는다(R12).
        if (providerEmail != null && providerEmail != submittedEmail) {
            log.warn(
                "SNS 가입: 제출 이메일이 provider 값과 다르다. provider={} submitted={} stored={}",
                request.provider.name,
                maskEmail(submittedEmail.value),
                maskEmail(providerEmail.value),
            )
        }

        val saved = save(member, member.createProfile(nickname, command.profileImageUrl), agreements, now)
        // 제출값이 아니라 저장된 주소로 보낸다(R12).
        val verification = if (saved.emailVerified) null else issueFirstVerification(saved, now)

        val memberId = requireNotNull(saved.id)
        eventPublisher.publish(MemberRegistered(memberId, saved.provider, saved.emailVerified, verification?.publicId, now))
        return SnsRegistrationResult(memberId, RegisterMemberResult(saved.emailVerified, verification?.publicId))
    }

    override fun registerByEmail(command: RegisterEmailMemberCommand): RegisterMemberResult {
        val now = clock.instant()
        val email = Email(command.email)
        val providerUserId = ProviderUserId(identifierProtector.protect(email.value))
        // 해시는 첫 쿼리 전에 만든다 — 그동안 DB 커넥션을 쥐지 않는다.
        val passwordHash = PasswordHash(passwordEncoder.encode(command.password))

        if (memberRepository.existsByProviderUserId(Provider.EMAIL, providerUserId)) {
            throw MemberAlreadyRegisteredException("이미 가입된 이메일입니다.")
        }
        emailUniqueness.ensureAvailable(Provider.EMAIL, email)

        val active = activeTerms(now)
        val agreements = agreementsOf(command.agreements, active, now)
        val member = Member.registerByEmail(
            email = email,
            providerUserId = providerUserId,
            passwordHash = passwordHash,
            ageVerified = command.ageVerified,
            agreements = agreements,
            requiredTermsCodes = active.requiredCodes,
        )

        // 닉네임에 이메일 로컬파트를 쓰지 않는다(R20) — 주소 일부가 표시명으로 새어 나간다.
        val saved = save(member, member.createProfile(nicknameGenerator.generate(), profileImageUrl = null), agreements, now)
        val verification = issueFirstVerification(saved, now)

        eventPublisher.publish(MemberRegistered(requireNotNull(saved.id), Provider.EMAIL, emailVerified = false, verification.publicId, now))
        return RegisterMemberResult(emailVerified = false, verificationPublicId = verification.publicId)
    }

    override fun verifyEmail(command: VerifyEmailCommand): VerifyEmailResult {
        val now = clock.instant()
        val verification = loadRegistrationChallenge(command.verificationPublicId, forUpdate = true)
        val member = memberRepository.findById(verification.memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

        val attempt = member.verifyEmail(verification, codeGenerator.hash(command.code), now)
        // 성패와 무관하게 시도 횟수·소진을 남긴다.
        memberRepository.saveEmailVerification(attempt.verification)

        val result = attempt.result
        if (result is VerificationResult.Failure) return VerifyEmailResult(verified = false, failureReason = result.reason.name)

        // 주소를 정정한 코드면 소유가 증명된 지금 회원의 이메일을 확정한다(R7-5).
        val verified = verification.targetEmail?.let { confirmRetargetedEmail(attempt.member, it) } ?: attempt.member
        memberRepository.saveEmailVerified(verified)
        eventPublisher.publish(EmailVerified(verification.memberId, verification.publicId, now))
        return VerifyEmailResult(verified = true, failureReason = null)
    }

    override fun resendVerification(command: ResendVerificationCommand): ResendVerificationResult {
        val now = clock.instant()
        val previous = loadRegistrationChallenge(command.verificationPublicId)
        val member = memberRepository.findById(previous.memberId)
            ?: throw VerificationNotFoundException("회원 정보를 찾을 수 없습니다.")

        val plainCode = codeGenerator.generate()
        val issue = member.reissueEmailVerification(
            previous = previous,
            existing = memberRepository.findEmailVerifications(previous.memberId, VerificationPurpose.REGISTRATION),
            code = newCode(plainCode),
            policy = verificationPolicy,
            now = now,
        )
        store(issue)
        eventPublisher.publish(VerificationEmailRequested((issue.issued.targetEmail ?: member.email).value, plainCode))
        return ResendVerificationResult(issue.issued.publicId, issue.issued.expiresAt)
    }

    override fun retargetEmailVerification(command: RetargetEmailVerificationCommand): RetargetEmailVerificationResult {
        val now = clock.instant()
        val newEmail = Email(command.newEmail)
        val member = memberRepository.findById(command.memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

        // 인증 완료 → 중복 → 쿨다운 순으로 본다. 순서가 곧 오류 코드의 우선순위다.
        member.ensureEmailUnverified()
        emailUniqueness.ensureAvailable(member.provider, newEmail, excludingMemberId = command.memberId)

        val plainCode = codeGenerator.generate()
        val issue = member.retargetEmailVerification(
            newEmail = newEmail,
            existing = memberRepository.findEmailVerifications(command.memberId, VerificationPurpose.REGISTRATION),
            code = newCode(plainCode),
            policy = verificationPolicy,
            now = now,
        )
        store(issue)
        eventPublisher.publish(VerificationEmailRequested(newEmail.value, plainCode))
        eventPublisher.publish(EmailVerificationRetargeted(command.memberId, member.provider))
        return RetargetEmailVerificationResult(issue.issued.publicId, issue.issued.expiresAt)
    }

    /** 회원·프로필·동의를 넣고 기본 역할(USER)을 준다. 관리 역할은 역할 부여로만 얻는다. */
    private fun save(member: Member, profile: Profile, agreements: List<Agreement>, now: Instant): Member {
        val saved = member.saved(memberRepository.insert(member, profile, agreements))
        memberRepository.findRole(Role.USER)?.let { role ->
            val change = saved.grantRole(role, held = emptyList())
            if (change is MemberRoleChange.Granted) memberRepository.addRole(change, now)
        }
        return saved
    }

    private fun issueFirstVerification(member: Member, now: Instant): EmailVerification {
        val plainCode = codeGenerator.generate()
        val verification = member.issueEmailVerification(newCode(plainCode), verificationPolicy, now)
        memberRepository.insertEmailVerification(verification)
        eventPublisher.publish(VerificationEmailRequested(member.email.value, plainCode))
        return verification
    }

    /** 발급 후 확정 사이에 다른 회원이 그 주소를 가져갔을 수 있어 다시 본다. */
    private fun confirmRetargetedEmail(member: Member, newEmail: Email): Member {
        emailUniqueness.ensureAvailable(member.provider, newEmail, excludingMemberId = member.id)
        val changed = member.replaceEmail(newEmail, ProviderUserId(identifierProtector.protect(newEmail.value)))
        memberRepository.saveEmail(changed)
        return changed
    }

    private fun store(issue: EmailVerificationIssue) {
        issue.consumed.forEach(memberRepository::saveEmailVerification)
        memberRepository.insertEmailVerification(issue.issued)
    }

    private fun newCode(plainCode: String) = VerificationCode(UUID.randomUUID(), codeGenerator.hash(plainCode))

    private fun activeTerms(now: Instant): ActiveTerms = ActiveTerms.of(memberRepository.findEffectiveTerms(now))

    private fun agreementsOf(inputs: List<AgreementInput>, active: ActiveTerms, now: Instant): List<Agreement> = Member.agreementsAtRegistration(inputs.associate { it.termsCode to it.agreed }, active, now)

    /**
     * 후보 중 처음으로 형식에 맞는 닉네임을 쓰고, 없으면 자동 생성한다(SPEC-0032 R29·R30).
     * - 잘라내지 않는다. 길이 규칙을 벗어나면 통째로 다음 후보로 넘어간다. 중복은 허용이라 저장소를 묻지 않는다(R31).
     */
    private fun nicknameFrom(vararg candidates: String?): Nickname = candidates.asSequence()
        .filterNotNull()
        .mapNotNull { runCatching { Nickname(it) }.getOrNull() }
        .firstOrNull()
        ?: nicknameGenerator.generate()

    /**
     * 핸들을 가입 인증 코드로만 읽는다. 이메일 변경 핸들이면 없는 것과 같이 답한다.
     * - 목적을 반드시 걸러야 한다. 이 경로는 인증이 없어, 거르지 않으면 남의 이메일 변경이 여기서 확정된다.
     */
    private fun loadRegistrationChallenge(publicId: UUID, forUpdate: Boolean = false): EmailVerification = memberRepository.findEmailVerification(publicId, forUpdate)
        ?.takeIf { it.purpose == VerificationPurpose.REGISTRATION }
        ?: throw VerificationNotFoundException("유효하지 않은 검증 요청입니다.")

    private companion object {
        val log = LoggerFactory.getLogger(MemberRegistrationService::class.java)
    }
}
