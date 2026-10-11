package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.auth.application.member.required.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.application.member.required.MemberSessionPort
import org.around.ieuri.auth.application.member.required.PasswordEncoderPort
import org.around.ieuri.auth.application.member.required.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.EmailVerification
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.NicknameGenerator
import org.around.ieuri.auth.domain.member.PasswordHash
import org.around.ieuri.auth.domain.member.PasswordReset
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.auth.domain.member.VerificationPolicy
import org.around.ieuri.auth.domain.member.VerificationPurpose
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/** 테스트가 함께 쓰는 인증 코드 정책: 10분 유효, 5회 시도, 재발송 쿨다운 60초. */
internal val testVerificationPolicy = VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60))

/**
 * 회원 쓰기 서비스 조립. 주제별 테스트가 자기가 보는 협력자만 넘기고 나머지는 기본값을 쓴다.
 * - 기본 협력자는 호출을 받아 주기만 한다. 호출 여부를 확인하려면 직접 만든 mock을 넘긴다.
 */
internal fun memberModifyService(
    memberRepository: MemberRepository,
    clock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
    eventPublisher: MemberEventPublisherPort = mockk(relaxUnitFun = true),
    sessions: MemberSessionPort = mockk(relaxUnitFun = true),
    codeGenerator: VerificationCodeGeneratorPort = fixedCodeGenerator(),
    passwordEncoder: PasswordEncoderPort = prefixingPasswordEncoder(),
    identifierProtector: LoginIdentifierProtectorPort = prefixingIdentifierProtector(),
    verificationPolicy: VerificationPolicy = testVerificationPolicy,
    nicknameGenerator: NicknameGenerator = NicknameGenerator(Random(0)),
): MemberModifyService = MemberModifyService(
    memberRepository = memberRepository,
    sessions = sessions,
    codeGenerator = codeGenerator,
    passwordEncoder = passwordEncoder,
    identifierProtector = identifierProtector,
    eventPublisher = eventPublisher,
    verificationPolicy = verificationPolicy,
    nicknameGenerator = nicknameGenerator,
    clock = clock,
)

/** 평문 코드는 늘 `123456`이고 해시는 `hashed:` 접두사다. */
internal fun fixedCodeGenerator(): VerificationCodeGeneratorPort = mockk {
    every { generate() } returns "123456"
    every { hash(any()) } answers { "hashed:${firstArg<String>()}" }
}

/** 비밀번호 해시 스텁. `encoded:` 접두사만 붙인다(실제 bcrypt는 어댑터 관심사). */
internal fun prefixingPasswordEncoder(): PasswordEncoderPort = mockk {
    every { encode(any()) } answers { "encoded:${firstArg<String>()}" }
}

/** 저장된 회원 한 행. 테스트마다 다른 값만 넘긴다. */
internal fun memberFixture(
    id: Long? = 100L,
    provider: Provider = Provider.EMAIL,
    providerUserId: String = "pid-1",
    email: String = "user@example.com",
    emailVerified: Boolean = true,
    status: MemberStatus = MemberStatus.ACTIVE,
    suspendedAt: Instant? = null,
    suspendedUntil: Instant? = null,
    suspensionReason: String? = null,
    passwordHash: String? = null,
): Member = Member(
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

/** 저장된 인증 코드 한 건. 기본은 아직 쓸 수 있는 가입 인증 코드다. */
internal fun emailVerificationFixture(
    publicId: UUID,
    expiresAt: Instant,
    memberId: Long = 100L,
    codeHash: String = "hashed:123456",
    attempts: Int = 0,
    maxAttempts: Int = 5,
    consumed: Boolean = false,
    verifiedAt: Instant? = null,
    createdAt: Instant? = null,
    purpose: VerificationPurpose = VerificationPurpose.REGISTRATION,
    targetEmail: String? = null,
): EmailVerification = EmailVerification(
    id = 1L,
    publicId = publicId,
    memberId = memberId,
    codeHash = codeHash,
    expiresAt = expiresAt,
    attempts = attempts,
    maxAttempts = maxAttempts,
    consumed = consumed,
    verifiedAt = verifiedAt,
    createdAt = createdAt,
    purpose = purpose,
    targetEmail = targetEmail?.let { Email(it) },
)

/** 저장된 비밀번호 재설정 코드 한 건. */
internal fun passwordResetFixture(
    expiresAt: Instant,
    id: Long = 1L,
    memberId: Long = 300L,
    codeHash: String = "hashed:123456",
    attempts: Int = 0,
    maxAttempts: Int = 5,
    consumed: Boolean = false,
    createdAt: Instant? = null,
): PasswordReset = PasswordReset(
    id = id,
    publicId = UUID.randomUUID(),
    memberId = memberId,
    codeHash = codeHash,
    expiresAt = expiresAt,
    attempts = attempts,
    maxAttempts = maxAttempts,
    consumed = consumed,
    createdAt = createdAt,
)

/** 보호 변환 스텁. 결정론적이고 평문과 구분되게 접두사만 붙인다(실제 HMAC은 어댑터 관심사). */
internal fun prefixingIdentifierProtector(): LoginIdentifierProtectorPort = object : LoginIdentifierProtectorPort {
    override fun protect(normalizedIdentifier: String) = "protected:$normalizedIdentifier"
}
