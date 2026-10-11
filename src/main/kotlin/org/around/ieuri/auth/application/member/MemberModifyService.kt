package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.application.member.provided.dto.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.member.provided.dto.AgreementInput
import org.around.ieuri.auth.application.member.provided.dto.AgreementStatusView
import org.around.ieuri.auth.application.member.provided.dto.AssignRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.ChangeEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.ChangeNicknameCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeResult
import org.around.ieuri.auth.application.member.provided.dto.EmailChangeRequested
import org.around.ieuri.auth.application.member.provided.dto.EmailCredentialView
import org.around.ieuri.auth.application.member.provided.dto.LifecycleResult
import org.around.ieuri.auth.application.member.provided.dto.ReactivateMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberResult
import org.around.ieuri.auth.application.member.provided.dto.RegisterSnsIdentityCommand
import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationResult
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordResult
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationResult
import org.around.ieuri.auth.application.member.provided.dto.RevokeRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RoleChangeResult
import org.around.ieuri.auth.application.member.provided.dto.SnsRegistrationResult
import org.around.ieuri.auth.application.member.provided.dto.SuspendMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.UpdateAgreementCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailResult
import org.around.ieuri.auth.application.member.provided.dto.WithdrawalTargetView
import org.around.ieuri.auth.application.member.provided.exception.EmailAlreadyInUseException
import org.around.ieuri.auth.application.member.provided.exception.InvalidEmailFormatException
import org.around.ieuri.auth.application.member.provided.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.provided.exception.RoleNotFoundException
import org.around.ieuri.auth.application.member.required.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.application.member.required.MemberSessionPort
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
import org.around.ieuri.auth.domain.member.ProfileImageObjectKey
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.auth.domain.member.Role
import org.around.ieuri.auth.domain.member.VerificationCode
import org.around.ieuri.auth.domain.member.VerificationNotFoundException
import org.around.ieuri.auth.domain.member.VerificationPolicy
import org.around.ieuri.auth.domain.member.VerificationPurpose
import org.around.ieuri.auth.domain.member.VerificationResult
import org.around.ieuri.auth.domain.member.event.EmailChanged
import org.around.ieuri.auth.domain.member.event.EmailVerificationRetargeted
import org.around.ieuri.auth.domain.member.event.EmailVerified
import org.around.ieuri.auth.domain.member.event.MemberReactivated
import org.around.ieuri.auth.domain.member.event.MemberRegistered
import org.around.ieuri.auth.domain.member.event.MemberRoleGranted
import org.around.ieuri.auth.domain.member.event.MemberRoleRevoked
import org.around.ieuri.auth.domain.member.event.MemberSuspended
import org.around.ieuri.auth.domain.member.event.MemberWithdrawn
import org.around.ieuri.auth.domain.member.event.PasswordResetCompleted
import org.around.ieuri.auth.domain.member.event.SnsUnlinkFailed
import org.around.ieuri.auth.domain.member.event.TermsAgreementChanged
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import org.around.ieuri.common.logging.maskEmail
import org.around.ieuri.common.persistence.ExposedConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 회원 쓰기. 루트([Member])가 판정하고 서비스는 바뀐 것만 쓴다. 평문 코드는 이벤트로만 나가고 커밋 뒤에 발송된다.
 * - 외부 호출이 끼는 흐름(SNS 가입·로그인·탈퇴·프로필 이미지)은 트랜잭션 없는 흐름 서비스가 이 서비스의 메서드를 단계로 부른다.
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class MemberModifyService(
    private val memberRepository: MemberRepository,
    private val sessions: MemberSessionPort,
    private val codeGenerator: VerificationCodeGeneratorPort,
    private val passwordEncoder: PasswordEncoderPort,
    private val identifierProtector: LoginIdentifierProtectorPort,
    private val eventPublisher: MemberEventPublisherPort,
    private val verificationPolicy: VerificationPolicy,
    private val nicknameGenerator: NicknameGenerator,
    private val clock: Clock,
) : MemberCommandUseCase {

    // ── 가입 ──

    override fun registerBySnsIdentity(command: RegisterSnsIdentityCommand): SnsRegistrationResult {
        val now = clock.instant()
        val request = command.request
        val providerUserId = ProviderUserId(command.providerUserId)
        if (memberRepository.existsByProviderUserId(request.provider, providerUserId)) throw MemberAlreadyRegisteredException()

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
        ensureEmailAvailable(request.provider, member.email)

        // 클라이언트가 R1(읽기 전용 표시)을 어긴 흔적. 저장값은 provider 값이므로 가입은 막지 않는다(R12).
        if (providerEmail != null && providerEmail != submittedEmail) {
            log.warn(
                "SNS 가입: 제출 이메일이 provider 값과 다르다. provider={} submitted={} stored={}",
                request.provider.name,
                maskEmail(submittedEmail.value),
                maskEmail(providerEmail.value),
            )
        }

        val saved = insert(member, member.createProfile(nickname, command.profileImageUrl), agreements, now)
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
        ensureEmailAvailable(Provider.EMAIL, email)

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
        val saved = insert(member, member.createProfile(nicknameGenerator.generate(), profileImageUrl = null), agreements, now)
        val verification = issueFirstVerification(saved, now)

        eventPublisher.publish(MemberRegistered(requireNotNull(saved.id), Provider.EMAIL, emailVerified = false, verification.publicId, now))
        return RegisterMemberResult(emailVerified = false, verificationPublicId = verification.publicId)
    }

    // ── 가입 이메일 인증 ──

    override fun verifyEmail(command: VerifyEmailCommand): VerifyEmailResult {
        val now = clock.instant()
        val verification = findChallenge(command.verificationPublicId, forUpdate = true)
        val member = findMember(verification.memberId)

        val attempt = member.verifyRegistrationEmail(verification, codeGenerator.hash(command.code), now)

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
        val previous = findChallenge(command.verificationPublicId)
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

    override fun retargetEmailVerification(command: RetargetEmailVerificationCommand, memberId: Long): RetargetEmailVerificationResult {
        val now = clock.instant()
        val newEmail = Email(command.newEmail)
        val member = findMember(memberId)

        // 인증 완료 → 중복 → 쿨다운 순으로 본다. 순서가 곧 오류 코드의 우선순위다.
        member.ensureEmailUnverified()
        ensureEmailAvailable(member.provider, newEmail, excludingMemberId = memberId)
        val plainCode = codeGenerator.generate()
        val issue = member.retargetEmailVerification(
            newEmail = newEmail,
            existing = memberRepository.findEmailVerifications(memberId, VerificationPurpose.REGISTRATION),
            code = newCode(plainCode),
            policy = verificationPolicy,
            now = now,
        )

        store(issue)
        eventPublisher.publish(VerificationEmailRequested(newEmail.value, plainCode))
        eventPublisher.publish(EmailVerificationRetargeted(memberId, member.provider))
        return RetargetEmailVerificationResult(issue.issued.publicId, issue.issued.expiresAt)
    }

    // ── 비밀번호 재설정 ──

    /** 가입되지 않은 주소는 MEMBER_NOT_FOUND, SNS 계정은 NOT_EMAIL_MEMBER로 거부한다. */
    override fun requestPasswordReset(command: RequestPasswordResetCommand) {
        val now = clock.instant()
        val email = Email(command.email).value
        val member = findEmailMember(email) ?: throw MemberNotFoundException("가입되지 않은 이메일입니다.")
        val memberId = requireNotNull(member.id)

        val plainCode = codeGenerator.generate()
        // 쿨다운이 없으면 주소를 아는 제3자가 특정인의 메일함에 코드를 무제한 보낼 수 있다.
        val issue = member.requestPasswordReset(memberRepository.findPasswordResets(memberId), newCode(plainCode), verificationPolicy, now)

        issue.consumed.forEach(memberRepository::consumePasswordReset)
        memberRepository.insertPasswordReset(issue.issued)
        eventPublisher.publish(PasswordResetRequested(memberId, email, plainCode))
    }

    /**
     * 계정 없음·SNS 계정·코드 없음·코드 불일치가 모두 `CODE_MISMATCH`다. 사유가 갈리면 인증 없이 계정을 열거할 수 있다.
     * - 세션 폐기는 트랜잭션 안에 둔다. 코드 확인 뒤에만 할 수 있고, 실패하면 코드 소진까지 되돌아가야 다시 시도할 수 있다.
     */
    override fun resetPassword(command: ResetPasswordCommand): ResetPasswordResult {
        val now = clock.instant()
        val member = findEmailMember(Email(command.email).value)?.takeIf { it.hasPassword } ?: return codeMismatch()
        val memberId = requireNotNull(member.id)
        val reset = member.activePasswordReset(memberRepository.findPasswordResets(memberId, forUpdate = true)) ?: return codeMismatch()

        val attempt = member.verifyPasswordReset(reset, codeGenerator.hash(command.code), now)

        // 성패와 무관하게 시도 횟수·소진을 남긴다.
        memberRepository.savePasswordReset(attempt.reset)
        val result = attempt.result
        if (result is VerificationResult.Failure) return ResetPasswordResult(success = false, failureReason = result.reason.name)

        memberRepository.savePassword(member.changePassword(PasswordHash(passwordEncoder.encode(command.newPassword))))
        sessions.revokeAll(memberId)
        eventPublisher.publish(PasswordResetCompleted(memberId))
        return ResetPasswordResult(success = true, failureReason = null)
    }

    // ── 이메일 변경 ──

    /** 형식 → 변경 가능 → 동일 주소 → 중복 순으로 본다. 회원의 이메일은 확인을 통과해야 바뀐다. */
    override fun changeEmail(command: ChangeEmailCommand, memberId: Long, ipAddress: String?): EmailChangeRequested {
        val newEmail = runCatching { Email(command.newEmail) }.getOrElse { throw InvalidEmailFormatException() }
        val member = findMember(memberId)

        member.ensureCanChangeEmail(newEmail)
        ensureEmailAvailable(member.provider, newEmail, excludingMemberId = memberId)
        val plainCode = codeGenerator.generate()
        // 같은 용도의 이전 코드만 폐기한다 — 가입 인증이 진행 중이면 그건 그대로 둔다.
        val issue = member.requestEmailChange(newEmail, emailChangeChallenges(memberId), newCode(plainCode), verificationPolicy, clock.instant())

        // 접속기록(기준 제8조). 개인정보(이메일) 변경이라 남겨야 하는 수행업무다.
        eventPublisher.publish(MemberAccessed(memberId, AccessType.EMAIL_CHANGE, ipAddress))
        return sendEmailChangeCode(issue, newEmail, plainCode)
    }

    /** 남의 핸들이면 없는 것과 같은 오류로 답한다. 성패와 무관하게 시도 횟수·소진을 남긴다. */
    override fun confirmEmailChange(command: ConfirmEmailChangeCommand, memberId: Long): ConfirmEmailChangeResult {
        val verification = findChallenge(command.verificationPublicId, forUpdate = true)
        val member = findMember(memberId)

        val attempt = member.confirmEmailChange(verification, codeGenerator.hash(command.code), clock.instant())

        memberRepository.saveEmailVerification(attempt.verification)
        val result = attempt.result
        if (result is VerificationResult.Failure) {
            return ConfirmEmailChangeResult(changed = false, email = null, failureReason = result.reason.name)
        }

        val newEmail = requireNotNull(verification.targetEmail) { "이메일 변경 챌린지에 대상 주소가 없습니다." }
        // 확인 시점에 다시 본다 — 요청 후 확인 사이에 다른 회원이 그 주소로 가입했을 수 있다.
        ensureEmailAvailable(member.provider, newEmail, excludingMemberId = memberId)
        memberRepository.saveEmail(member.replaceEmail(newEmail, ProviderUserId(identifierProtector.protect(newEmail.value))))
        eventPublisher.publish(EmailChanged(memberId, member.provider, loginIdentifierUpdated = member.provider == Provider.EMAIL))
        return ConfirmEmailChangeResult(changed = true, email = newEmail.value, failureReason = null)
    }

    /** 쿨다운은 이메일 변경 용도로만 센다. 대상 주소는 이전 코드에서 이어받는다. */
    override fun resendEmailChange(command: ResendEmailChangeCommand, memberId: Long): EmailChangeRequested {
        val previous = findChallenge(command.verificationPublicId)
        val member = findMember(memberId)

        val plainCode = codeGenerator.generate()
        val issue = member.reissueEmailChange(previous, emailChangeChallenges(memberId), newCode(plainCode), verificationPolicy, clock.instant())

        return sendEmailChangeCode(issue, requireNotNull(issue.issued.targetEmail), plainCode)
    }

    // ── 정지·복구·탈퇴 ──

    /**
     * 상태만 SUSPENDED로 바꾸고 개인정보는 보존한다.
     * - 세션 폐기는 트랜잭션 안에 둔다. 정지가 정해진 뒤에만 끊어야 하고, 폐기가 실패하면 정지도 되돌아가야 한다.
     */
    override fun suspend(command: SuspendMemberCommand): LifecycleResult {
        val now = clock.instant()
        val member = findMember(command.memberId)

        val suspended = member.suspend(command.reason, command.suspendedUntil, now)

        memberRepository.saveStatus(suspended)
        sessions.revokeAll(command.memberId)
        eventPublisher.publish(MemberSuspended(command.memberId, command.reason, command.suspendedUntil, now))
        return LifecycleResult(success = true)
    }

    override fun reactivate(command: ReactivateMemberCommand): LifecycleResult {
        val member = findMember(command.memberId)

        val reactivated = member.reactivate()

        memberRepository.saveStatus(reactivated)
        eventPublisher.publish(MemberReactivated(command.memberId, clock.instant()))
        return LifecycleResult(success = true)
    }

    override fun withdrawalTarget(memberId: Long): WithdrawalTargetView {
        val member = findMember(memberId)
        return WithdrawalTargetView(
            provider = member.provider,
            providerUserId = member.providerUserId.value,
            providerRefreshToken = memberRepository.findProviderRefreshToken(memberId),
        )
    }

    /** 행 삭제(hard delete). [MemberWithdrawn]은 커밋 뒤 다른 context로 나간다(SPEC-0011 R17). */
    override fun erase(memberId: Long, provider: Provider, unlinkFailureReason: String?) {
        memberRepository.delete(memberId)
        // 해제 실패는 provider와 사유만 남긴다. 토큰·이메일은 남기지 않는다(R11).
        unlinkFailureReason?.let { eventPublisher.publish(SnsUnlinkFailed(memberId, provider, it)) }
        eventPublisher.publish(MemberWithdrawn(memberId, provider, clock.instant()))
    }

    // ── 프로필 ──

    /** 형식만 보고 중복은 보지 않는다(SPEC-0032 R31). 형식 오류가 회원 부재보다 먼저다. 프로필 행이 없으면 없는 회원과 같이 답한다. */
    override fun changeNickname(command: ChangeNicknameCommand, memberId: Long): String {
        val nickname = Nickname(command.nickname)
        findMember(memberId)

        if (!memberRepository.saveNickname(memberId, nickname)) throw MemberNotFoundException()
        return nickname.value
    }

    override fun attachProfileImage(memberId: Long, pendingObjectKey: String): String? {
        val member = findMember(memberId)
        val previousKey = memberRepository.findProfileImageKey(memberId)

        val confirmed = member.attachProfileImage(ProfileImageObjectKey.pendingOf(pendingObjectKey, memberId))

        if (!memberRepository.saveProfileImage(memberId, confirmed.value)) throw MemberNotFoundException()
        return previousKey
    }

    override fun detachProfileImage(memberId: Long): String? {
        val previousKey = memberRepository.findProfileImageKey(memberId)

        if (!memberRepository.saveProfileImage(memberId, objectKey = null)) throw MemberNotFoundException()
        return previousKey
    }

    // ── 약관 동의 ──

    /** 철회해도 다른 오퍼레이션을 거절하지 않는다. 이용 제한은 클라이언트가 `required=true, agreed=false`를 보고 건다(R11). */
    override fun updateAgreement(command: UpdateAgreementCommand, memberId: Long): AgreementStatusView {
        val now = clock.instant()
        val active = activeTerms(now)
        val member = findMember(memberId)

        val agreement = member.decideAgreement(command.termsCode, command.agreed, active, now)

        saveAgreements(memberId, listOf(agreement), active, command.agreed)
        return agreementStatuses(memberId, active).first { it.termsCode == command.termsCode }
    }

    /** 동의만 받는다. 이미 동의한 코드를 다시 내도 오류가 아니다. */
    override fun agreeToRequiredTerms(command: AgreeToRequiredTermsCommand, memberId: Long): List<AgreementStatusView> {
        val now = clock.instant()
        val active = activeTerms(now)
        val member = findMember(memberId)

        val agreements = member.agreeToRequiredTerms(command.termsCodes, active, now)

        saveAgreements(memberId, agreements, active, agreed = true)
        return agreementStatuses(memberId, active)
    }

    // ── 역할 ──

    /** 멱등이다. 대상의 accessToken에는 다음 재발급(최대 access TTL) 뒤에 반영된다. */
    override fun assignRole(command: AssignRoleCommand, actorMemberId: Long): RoleChangeResult {
        val member = findMember(command.targetMemberId)
        val role = findRole(command.roleCode)

        val change = member.grantRole(role, memberRepository.findRolesOf(command.targetMemberId))

        if (change is MemberRoleChange.Granted) {
            memberRepository.addRole(change, clock.instant())
            eventPublisher.publish(MemberRoleGranted(command.targetMemberId, command.roleCode, actorMemberId))
        }
        return RoleChangeResult(success = true, changed = change !is MemberRoleChange.Unchanged)
    }

    /** 멱등이다. 자기 강등 검사와 회수 사이의 경합(동시 회수 두 건이 모두 통과)은 받아들인 잔여 위험이다. */
    override fun revokeRole(command: RevokeRoleCommand, actorMemberId: Long): RoleChangeResult {
        val member = findMember(command.targetMemberId)
        val role = findRole(command.roleCode)

        val change = member.revokeRole(role, memberRepository.findRolesOf(command.targetMemberId), actorMemberId)

        if (change is MemberRoleChange.Revoked) {
            memberRepository.removeRole(change)
            eventPublisher.publish(MemberRoleRevoked(command.targetMemberId, command.roleCode, actorMemberId))
        }
        return RoleChangeResult(success = true, changed = change !is MemberRoleChange.Unchanged)
    }

    // ── 로그인 ──

    override fun admitSns(provider: Provider, providerUserId: String): Long {
        val member = memberRepository.findByProviderUserId(provider, ProviderUserId(providerUserId))
            ?: throw MemberNotFoundException("가입되지 않은 계정입니다. 회원가입이 필요합니다.")
        return admit(member)
    }

    override fun findEmailCredential(loginIdentifier: String): EmailCredentialView? = memberRepository.findByProviderUserId(Provider.EMAIL, ProviderUserId(loginIdentifier))
        ?.let { EmailCredentialView(memberId = requireNotNull(it.id), passwordHash = it.passwordHash?.value) }

    override fun admit(memberId: Long) {
        admit(findMember(memberId))
    }

    override fun keepProviderRefreshToken(memberId: Long, token: String) {
        memberRepository.findById(memberId)?.keepProviderRefreshToken(token)?.let(memberRepository::saveProviderRefreshToken)
    }

    // ── 공통 ──

    private fun findMember(memberId: Long): Member = memberRepository.findById(memberId) ?: throw MemberNotFoundException()

    private fun findEmailMember(email: String): Member? = memberRepository.findByProviderUserId(Provider.EMAIL, ProviderUserId(identifierProtector.protect(email)))

    private fun findRole(code: String): Role = memberRepository.findRole(code) ?: throw RoleNotFoundException("존재하지 않는 역할입니다: $code")

    /** 핸들로 코드를 읽는다. 이 회원·이 용도의 코드가 맞는지는 루트가 판정한다. */
    private fun findChallenge(publicId: UUID, forUpdate: Boolean = false): EmailVerification = memberRepository.findEmailVerification(publicId, forUpdate)
        ?: throw VerificationNotFoundException()

    private fun emailChangeChallenges(memberId: Long): List<EmailVerification> = memberRepository.findEmailVerifications(memberId, VerificationPurpose.EMAIL_CHANGE)

    /**
     * `(provider, email)` 유니크 판정(SPEC-0024 R5). 다른 provider의 같은 주소는 충돌이 아니다(POL-002).
     * - 판정 키는 정규화된 주소의 결정론적 HMAC이다(암호문은 IV가 달라 등호 조회가 안 된다).
     * - 예외에 상대 회원의 정보를 담지 않는다(POL-013). [excludingMemberId]는 본인 제외 기준이다.
     */
    private fun ensureEmailAvailable(provider: Provider, email: Email, excludingMemberId: Long? = null) {
        if (memberRepository.existsByEmailIdentifier(provider, identifierProtector.protect(email.value), excludingMemberId)) {
            throw EmailAlreadyInUseException()
        }
    }

    /** 회원·프로필·동의를 넣고 기본 역할(USER)을 준다. 관리 역할은 역할 부여로만 얻는다. */
    private fun insert(member: Member, profile: Profile, agreements: List<Agreement>, now: Instant): Member {
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
        ensureEmailAvailable(member.provider, newEmail, excludingMemberId = member.id)
        val changed = member.replaceEmail(newEmail, ProviderUserId(identifierProtector.protect(newEmail.value)))
        memberRepository.saveEmail(changed)
        return changed
    }

    private fun sendEmailChangeCode(issue: EmailVerificationIssue, toEmail: Email, plainCode: String): EmailChangeRequested {
        store(issue)
        eventPublisher.publish(VerificationEmailRequested(toEmail.value, plainCode))
        return EmailChangeRequested(issue.issued.publicId, issue.issued.expiresAt)
    }

    private fun store(issue: EmailVerificationIssue) {
        issue.consumed.forEach(memberRepository::consumeEmailVerification)
        memberRepository.insertEmailVerification(issue.issued)
    }

    private fun newCode(plainCode: String) = VerificationCode(UUID.randomUUID(), codeGenerator.hash(plainCode))

    private fun activeTerms(now: Instant): ActiveTerms = ActiveTerms.of(memberRepository.findEffectiveTerms(now))

    private fun agreementsOf(inputs: List<AgreementInput>, active: ActiveTerms, now: Instant): List<Agreement> = Member.agreementsAtRegistration(inputs.associate { it.termsCode to it.agreed }, active, now)

    private fun saveAgreements(memberId: Long, agreements: List<Agreement>, active: ActiveTerms, agreed: Boolean) {
        if (agreements.isEmpty()) return
        memberRepository.saveAgreements(memberId, agreements)
        eventPublisher.publish(TermsAgreementChanged(memberId, agreements.mapNotNull { active.find(it.termsCode) }, agreed))
    }

    private fun agreementStatuses(memberId: Long, active: ActiveTerms): List<AgreementStatusView> = active.statusOf(memberRepository.findAgreements(memberId)).map(AgreementStatusView::of)

    /**
     * 후보 중 처음으로 형식에 맞는 닉네임을 쓰고, 없으면 자동 생성한다(SPEC-0032 R29·R30).
     * - 잘라내지 않는다. 길이 규칙을 벗어나면 통째로 다음 후보로 넘어간다. 중복은 허용이라 저장소를 묻지 않는다(R31).
     */
    private fun nicknameFrom(vararg candidates: String?): Nickname = candidates.asSequence()
        .filterNotNull()
        .mapNotNull { runCatching { Nickname(it) }.getOrNull() }
        .firstOrNull()
        ?: nicknameGenerator.generate()

    /** 기간제 정지가 만료돼 복구됐을 때만 상태를 쓴다. */
    private fun admit(member: Member): Long {
        val admitted = member.admit(clock.instant())
        if (admitted != member) memberRepository.saveStatus(admitted)
        return requireNotNull(member.id)
    }

    private fun codeMismatch() = ResetPasswordResult(success = false, failureReason = CODE_MISMATCH)

    private companion object {
        const val CODE_MISMATCH = "CODE_MISMATCH"
        val log = LoggerFactory.getLogger(MemberModifyService::class.java)
    }
}
