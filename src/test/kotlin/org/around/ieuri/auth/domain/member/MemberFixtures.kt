package org.around.ieuri.auth.domain.member

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** 회원 도메인 테스트가 함께 쓰는 고정값. 필드마다 값이 달라 뒤바뀌면 드러난다. */
internal object MemberFixtures {

    const val MEMBER_ID = 7L

    /** 남의 코드를 만들 때 쓰는 다른 회원. */
    const val OTHER_MEMBER_ID = 8L

    val NOW: Instant = Instant.parse("2026-10-05T03:00:00Z")

    /** 만료 10분, 시도 3회, 재발송 쿨다운 60초. */
    val POLICY = VerificationPolicy(ttl = Duration.ofMinutes(10), maxAttempts = 3, resendCooldown = Duration.ofSeconds(60))

    val CODE = VerificationCode(publicId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"), codeHash = "새-코드-해시")

    fun member(
        id: Long? = MEMBER_ID,
        provider: Provider = Provider.EMAIL,
        providerUserId: String = "login-id-현재",
        email: String = "current@example.com",
        emailVerified: Boolean = false,
        status: MemberStatus = MemberStatus.ACTIVE,
        suspendedAt: Instant? = null,
        suspendedUntil: Instant? = null,
        suspensionReason: String? = null,
        passwordHash: String? = "hash-현재",
    ) = Member(
        id = id,
        provider = provider,
        providerUserId = ProviderUserId(providerUserId),
        email = Email(email),
        emailVerified = emailVerified,
        status = status,
        ageVerified = true,
        suspendedAt = suspendedAt,
        suspendedUntil = suspendedUntil,
        suspensionReason = suspensionReason,
        passwordHash = passwordHash?.let { PasswordHash(it) },
    )

    /** 저장된 인증 코드. [n]으로 id·핸들·해시를 서로 다르게 만든다. */
    fun verification(
        n: Int = 1,
        memberId: Long = MEMBER_ID,
        codeHash: String = "해시-$n",
        expiresAt: Instant = NOW.plusSeconds(600),
        attempts: Int = 0,
        maxAttempts: Int = 3,
        consumed: Boolean = false,
        verifiedAt: Instant? = null,
        createdAt: Instant? = NOW.minusSeconds(3600),
        purpose: VerificationPurpose = VerificationPurpose.REGISTRATION,
        targetEmail: Email? = null,
    ) = EmailVerification(
        id = 100L + n,
        publicId = UUID(0L, n.toLong()),
        memberId = memberId,
        codeHash = codeHash,
        expiresAt = expiresAt,
        attempts = attempts,
        maxAttempts = maxAttempts,
        consumed = consumed,
        verifiedAt = verifiedAt,
        createdAt = createdAt,
        purpose = purpose,
        targetEmail = targetEmail,
    )

    /** 저장된 재설정 코드. */
    fun reset(
        n: Int = 1,
        codeHash: String = "재설정-해시-$n",
        expiresAt: Instant = NOW.plusSeconds(600),
        attempts: Int = 0,
        maxAttempts: Int = 3,
        consumed: Boolean = false,
        createdAt: Instant? = NOW.minusSeconds(3600),
    ) = PasswordReset(
        id = 200L + n,
        publicId = UUID(1L, n.toLong()),
        memberId = MEMBER_ID,
        codeHash = codeHash,
        expiresAt = expiresAt,
        attempts = attempts,
        maxAttempts = maxAttempts,
        consumed = consumed,
        createdAt = createdAt,
    )

    fun role(id: Long, code: String, vararg permissions: String) = Role(
        id = id,
        code = code,
        name = "$code-이름",
        permissions = permissions.map { Permission(it) }.toSet(),
    )

    val SERVICE = Terms(id = 11L, code = "SERVICE", version = "1.0", required = true)
    val PRIVACY = Terms(id = 12L, code = "PRIVACY", version = "2.0", required = true)
    val MARKETING = Terms(id = 13L, code = "MARKETING", version = "3.0", required = false)

    val ACTIVE_TERMS: ActiveTerms = ActiveTerms.of(listOf(SERVICE, PRIVACY, MARKETING))
}
