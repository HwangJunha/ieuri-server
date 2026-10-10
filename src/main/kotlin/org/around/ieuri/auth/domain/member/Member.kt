package org.around.ieuri.auth.domain.member

import java.time.Instant

/**
 * 회원 애그리게이트 루트. `member` 한 행만 담는다 — 프로필·동의·역할·인증 코드는 루트가 만들어 따로 저장한다.
 * - 탈퇴는 상태 전이가 아니라 행 삭제라 도메인 상태로 남지 않는다.
 */
data class Member(
    val id: Long?,
    val provider: Provider,
    /** EMAIL 회원은 정규화한 이메일의 보호 변환값이다(SPEC-0012 R1). */
    val providerUserId: ProviderUserId,
    val email: Email,
    val emailVerified: Boolean,
    val status: MemberStatus,
    val ageVerified: Boolean,
    val suspendedAt: Instant?,
    val suspendedUntil: Instant?,
    val suspensionReason: String?,
    /** 자체(EMAIL) 회원의 비밀번호 해시. SNS 회원은 null. */
    val passwordHash: PasswordHash?,
) {
    /** 저장 직후 DB가 준 id를 얹는다. */
    fun saved(id: Long): Member = copy(id = id)

    // ── 프로필 ──

    /** 가입 때 만드는 프로필. */
    fun createProfile(nickname: Nickname, profileImageUrl: String?): Profile = Profile(nickname, profileImageUrl)

    /** 확인이 끝난 프로필 이미지 키. 이 회원에게 발급된 키만 받는다. */
    fun attachProfileImage(key: ProfileImageObjectKey): ProfileImageObjectKey {
        if (!key.ownedBy(savedId())) throw ObjectKeyNotFoundException()
        return key.toConfirmed()
    }

    // ── 가입 이메일 인증 ──

    /** 가입 직후의 첫 인증 코드. */
    fun issueEmailVerification(code: VerificationCode, policy: VerificationPolicy, now: Instant): EmailVerification = EmailVerification.issue(savedId(), code, policy, now, VerificationPurpose.REGISTRATION, targetEmail = null)

    /**
     * 가입 인증 코드 재발송. 정정한 대기 주소는 이어받는다 — 회원의 이메일로 되돌리면 정정이 조용히 취소된다.
     * @param existing 이 회원의 가입 인증 코드 전부
     */
    fun reissueEmailVerification(
        previous: EmailVerification,
        existing: List<EmailVerification>,
        code: VerificationCode,
        policy: VerificationPolicy,
        now: Instant,
    ): EmailVerificationIssue {
        ensureOwn(previous, VerificationPurpose.REGISTRATION)
        if (!policy.cooldownPassed(lastIssuedAt(existing), now)) throw VerificationRateLimitedException()
        return EmailVerificationIssue(
            consumed = existing.filter { it.active }.map { it.consume() },
            issued = EmailVerification.issue(savedId(), code, policy, now, VerificationPurpose.REGISTRATION, previous.targetEmail),
        )
    }

    /** 인증 대기 주소를 정정할 수 있는 회원인지(SPEC-0024 R7-2). 인증을 마쳤으면 거부한다. */
    fun ensureEmailUnverified() {
        if (emailVerified) throw AlreadyVerifiedException()
    }

    /**
     * 인증 대기 주소 정정(SPEC-0024 R7). 회원의 이메일은 아직 바뀌지 않는다 — 새 주소는 코드가 들고 있다가 통과하면 확정된다.
     * - 쿨다운은 재발송과 같은 창을 쓰되 코드는 RESEND_COOLDOWN_ACTIVE다.
     */
    fun retargetEmailVerification(
        newEmail: Email,
        existing: List<EmailVerification>,
        code: VerificationCode,
        policy: VerificationPolicy,
        now: Instant,
    ): EmailVerificationIssue {
        ensureEmailUnverified()
        if (!policy.cooldownPassed(lastIssuedAt(existing), now)) throw ResendCooldownActiveException()
        return EmailVerificationIssue(
            consumed = existing.filter { it.active }.map { it.consume() },
            issued = EmailVerification.issue(savedId(), code, policy, now, VerificationPurpose.REGISTRATION, newEmail),
        )
    }

    /**
     * 가입 인증 코드 확인. 통과하면 회원이 인증된 상태가 된다. 주소 확정은 [replaceEmail]로 따로 한다.
     * - 이 회원의 가입 인증 코드만 받는다. 이메일 변경 코드가 여기서 통과하면 인증 없이 남의 변경이 확정된다.
     */
    fun verifyRegistrationEmail(verification: EmailVerification, inputCodeHash: String, now: Instant): EmailVerificationAttempt {
        ensureOwn(verification, VerificationPurpose.REGISTRATION)
        val (attempted, result) = verification.verify(inputCodeHash, now)
        return EmailVerificationAttempt(if (result is VerificationResult.Success) copy(emailVerified = true) else this, attempted, result)
    }

    // ── 이메일 변경 ──

    /** 이메일 변경 요청(SPEC-0011 R7). 자체(EMAIL) 회원만, 지금과 다른 주소로만 된다. 진행 중이던 변경 코드는 폐기한다. */
    fun requestEmailChange(
        newEmail: Email,
        existing: List<EmailVerification>,
        code: VerificationCode,
        policy: VerificationPolicy,
        now: Instant,
    ): EmailVerificationIssue {
        ensureCanChangeEmail(newEmail)
        return emailChangeIssue(newEmail, existing, code, policy, now)
    }

    /** 변경 가능 여부만 본다 — 중복 확인(저장소)보다 먼저 판정해야 할 때 쓴다. */
    fun ensureCanChangeEmail(newEmail: Email) {
        if (provider != Provider.EMAIL) throw NotEmailMemberException("SNS 계정은 이메일을 변경할 수 없습니다.")
        if (email == newEmail) throw SameAsCurrentEmailException()
    }

    /** 이메일 변경 코드 재발송. 대상 주소는 이전 코드에서 이어받고, 쿨다운은 변경 용도로만 센다. */
    fun reissueEmailChange(
        previous: EmailVerification,
        existing: List<EmailVerification>,
        code: VerificationCode,
        policy: VerificationPolicy,
        now: Instant,
    ): EmailVerificationIssue {
        ensureOwn(previous, VerificationPurpose.EMAIL_CHANGE)
        if (!policy.cooldownPassed(lastIssuedAt(existing), now)) throw ResendCooldownActiveException()
        val target = requireNotNull(previous.targetEmail) { "이메일 변경 챌린지에 대상 주소가 없습니다." }
        return emailChangeIssue(target, existing, code, policy, now)
    }

    /** 이메일 변경 코드 확인. 이 회원의 이메일 변경 코드만 받는다. 통과해도 회원은 그대로다 — 주소는 [replaceEmail]이 바꾼다. */
    fun confirmEmailChange(verification: EmailVerification, inputCodeHash: String, now: Instant): EmailVerificationAttempt {
        ensureOwn(verification, VerificationPurpose.EMAIL_CHANGE)
        val (attempted, result) = verification.verify(inputCodeHash, now)
        return EmailVerificationAttempt(this, attempted, result)
    }

    /**
     * 소유가 증명된 주소로 이메일을 바꾼다. 자체(EMAIL) 회원은 로그인 식별자도 함께 바뀐다(R8) — 갈리면 계정이 잠긴다.
     * @param loginIdentifier 새 주소의 보호 변환값. SNS 회원은 쓰지 않는다.
     */
    fun replaceEmail(newEmail: Email, loginIdentifier: ProviderUserId): Member = copy(
        email = newEmail,
        providerUserId = if (provider == Provider.EMAIL) loginIdentifier else providerUserId,
    )

    private fun emailChangeIssue(
        target: Email,
        existing: List<EmailVerification>,
        code: VerificationCode,
        policy: VerificationPolicy,
        now: Instant,
    ) = EmailVerificationIssue(
        consumed = existing.filter { it.active }.map { it.consume() },
        issued = EmailVerification.issue(savedId(), code, policy, now, VerificationPurpose.EMAIL_CHANGE, target),
    )

    // ── 비밀번호 재설정 ──

    /** 비밀번호가 있는 회원인지. SNS 회원은 없다. */
    val hasPassword: Boolean get() = passwordHash != null

    /** 재설정 코드 발급. SNS 회원은 거부하고, 쿨다운 안이면 거부한다. 이전 코드는 폐기한다. */
    fun requestPasswordReset(
        existing: List<PasswordReset>,
        code: VerificationCode,
        policy: VerificationPolicy,
        now: Instant,
    ): PasswordResetIssue {
        if (!hasPassword) throw NotEmailMemberException("SNS로 가입한 계정입니다. 해당 SNS로 로그인해 주세요.")
        if (!policy.cooldownPassed(existing.mapNotNull { it.createdAt }.maxOrNull(), now)) throw ResendCooldownActiveException()
        return PasswordResetIssue(
            consumed = existing.filter { it.active }.map { it.consume() },
            issued = PasswordReset.issue(savedId(), code, policy, now),
        )
    }

    /** 지금 쓸 수 있는 재설정 코드 — 소진되지 않은 것 중 가장 최근 것. */
    fun activePasswordReset(existing: List<PasswordReset>): PasswordReset? = existing.filter { it.active }.maxByOrNull { it.createdAt ?: Instant.MIN }

    fun verifyPasswordReset(reset: PasswordReset, inputCodeHash: String, now: Instant): PasswordResetAttempt {
        val (attempted, result) = reset.verify(inputCodeHash, now)
        return PasswordResetAttempt(attempted, result)
    }

    fun changePassword(newHash: PasswordHash): Member = copy(passwordHash = newHash)

    // ── 약관 동의 ──

    /** 약관 동의를 켜고 끈다(SPEC-0011 R10). 필수 약관도 철회할 수 있다. */
    fun decideAgreement(code: String, agreed: Boolean, active: ActiveTerms, now: Instant): Agreement {
        val terms = active.find(code) ?: throw TermsNotFoundException()
        return agreementOf(terms, agreed, now)
    }

    /** 미동의 필수 약관에 동의(R12). 선택 약관 코드가 섞이면 전부 거절한다. 같은 코드는 한 번만 센다. */
    fun agreeToRequiredTerms(codes: List<String>, active: ActiveTerms, now: Instant): List<Agreement> = codes.distinct().map { code ->
        val terms = active.find(code) ?: throw TermsNotFoundException()
        if (!terms.required) throw TermsCodeNotRequiredException()
        agreementOf(terms, agreed = true, now)
    }

    private fun agreementOf(terms: Terms, agreed: Boolean, now: Instant) = Agreement(
        termsId = terms.id,
        termsCode = terms.code,
        termsVersion = terms.version,
        agreed = agreed,
        agreedAt = now,
    )

    // ── 역할 ──

    /** 역할 부여. 이미 가졌으면 바꾸지 않는다. */
    fun grantRole(role: Role, held: List<Role>): MemberRoleChange = if (held.any { it.code == role.code }) MemberRoleChange.Unchanged else MemberRoleChange.Granted(savedId(), role)

    /** 역할 회수. 본인의 마지막 관리 역할은 스스로 거둘 수 없다(자기 강등 방지). 없던 역할이면 바꾸지 않는다. */
    fun revokeRole(role: Role, held: List<Role>, actorMemberId: Long): MemberRoleChange {
        if (actorMemberId == id && role.isAdministrative() && held.none { it.code != role.code && it.isAdministrative() }) {
            throw SelfDemotionException()
        }
        return if (held.any { it.code == role.code }) MemberRoleChange.Revoked(savedId(), role) else MemberRoleChange.Unchanged
    }

    // ── SNS 연결 ──

    /** 탈퇴 때 연결을 끊을 provider 토큰. 자체(EMAIL) 회원은 대상이 아니라 null이다(SPEC-0012 R5). */
    fun keepProviderRefreshToken(token: String): ProviderRefreshToken? = if (provider == Provider.EMAIL) null else ProviderRefreshToken(savedId(), token)

    // ── 정지·복구·로그인 ──

    /** 관리자 정지. 이미 정지 상태면 사유·기간을 갱신한다. */
    fun suspend(reason: String, until: Instant?, now: Instant): Member {
        if (reason.isBlank()) throw InvalidSuspensionException("정지 사유는 필수입니다.")
        if (until != null && !until.isAfter(now)) throw InvalidSuspensionException("정지 만료일은 현재 이후여야 합니다.")
        return copy(status = MemberStatus.SUSPENDED, suspendedAt = now, suspendedUntil = until, suspensionReason = reason)
    }

    /** 복구. 정지 상태에서만 된다. */
    fun reactivate(): Member {
        if (status != MemberStatus.SUSPENDED) throw NotSuspendedException()
        return copy(status = MemberStatus.ACTIVE, suspendedAt = null, suspendedUntil = null, suspensionReason = null)
    }

    /**
     * 로그인 허용 판정. 기간제 정지가 만료됐으면 복구된 회원을 돌려주고, 유효한 정지면 거부한다.
     * - 무기한 정지(until = null)는 만료되지 않는다. 바뀐 것이 없으면 같은 회원이다.
     */
    fun admit(now: Instant): Member {
        if (status == MemberStatus.ACTIVE) return this
        val until = suspendedUntil
        if (until != null && !now.isBefore(until)) return reactivate()
        throw InactiveMemberException("정지된 계정입니다." + (suspensionReason?.let { " 사유: $it" } ?: ""))
    }

    /** 남의 코드나 다른 용도의 코드는 없는 것과 같이 답한다(핸들 존재 비노출). */
    private fun ensureOwn(verification: EmailVerification, purpose: VerificationPurpose) {
        if (verification.memberId != id || verification.purpose != purpose) throw VerificationNotFoundException()
    }

    private fun savedId(): Long = checkNotNull(id) { "저장되지 않은 회원입니다." }

    private fun lastIssuedAt(existing: List<EmailVerification>): Instant? = existing.mapNotNull { it.createdAt }.maxOrNull()

    companion object {
        /**
         * SNS 가입(SPEC-0024 R2·R3·R12). 인증 면제는 `providerEmail != null` 하나로 판정한다.
         * @param providerEmail provider가 준 이메일. 있으면 이 값이 저장되고 인증이 면제된다.
         * @param fallbackEmail provider가 이메일을 주지 않았을 때 쓸 사용자 입력값(R3).
         */
        fun register(
            provider: Provider,
            providerUserId: ProviderUserId,
            providerEmail: Email?,
            fallbackEmail: Email,
            ageVerified: Boolean,
            agreements: List<Agreement>,
            requiredTermsCodes: Set<String>,
        ): Member {
            ensureEligible(ageVerified, agreements, requiredTermsCodes)
            return newMember(provider, providerUserId, providerEmail ?: fallbackEmail, emailVerified = providerEmail != null, passwordHash = null)
        }

        /** 자체(EMAIL) 가입. 미인증으로 시작하며 약관·만 14세 규칙은 SNS 가입과 같다. */
        fun registerByEmail(
            email: Email,
            providerUserId: ProviderUserId,
            passwordHash: PasswordHash,
            ageVerified: Boolean,
            agreements: List<Agreement>,
            requiredTermsCodes: Set<String>,
        ): Member {
            ensureEligible(ageVerified, agreements, requiredTermsCodes)
            return newMember(Provider.EMAIL, providerUserId, email, emailVerified = false, passwordHash = passwordHash)
        }

        /** 가입 때 제출한 동의를 시행 중인 약관 전부에 대해 만든다. 제출하지 않은 약관은 미동의로 남긴다. */
        fun agreementsAtRegistration(submitted: Map<String, Boolean>, active: ActiveTerms, now: Instant): List<Agreement> = active.all.map { terms ->
            Agreement(
                termsId = terms.id,
                termsCode = terms.code,
                termsVersion = terms.version,
                agreed = submitted[terms.code] ?: false,
                agreedAt = now,
            )
        }

        private fun ensureEligible(ageVerified: Boolean, agreements: List<Agreement>, requiredTermsCodes: Set<String>) {
            if (!ageVerified) throw UnderageRegistrationException()
            val missing = requiredTermsCodes - agreements.filter { it.agreed }.map { it.termsCode }.toSet()
            if (missing.isNotEmpty()) throw RequiredTermsNotAgreedException("필수 약관에 동의해야 가입할 수 있습니다. 미동의: $missing")
        }

        private fun newMember(
            provider: Provider,
            providerUserId: ProviderUserId,
            email: Email,
            emailVerified: Boolean,
            passwordHash: PasswordHash?,
        ) = Member(
            id = null,
            provider = provider,
            providerUserId = providerUserId,
            email = email,
            emailVerified = emailVerified,
            status = MemberStatus.ACTIVE,
            ageVerified = true,
            suspendedAt = null,
            suspendedUntil = null,
            suspensionReason = null,
            passwordHash = passwordHash,
        )
    }
}
