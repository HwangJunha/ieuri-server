package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.auth.application.command.AgreementInput
import org.around.ieuri.auth.application.command.RegisterMemberCommand
import org.around.ieuri.auth.application.command.VerifyEmailCommand
import org.around.ieuri.auth.application.event.VerificationEmailRequested
import org.around.ieuri.auth.application.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.around.ieuri.auth.application.port.out.client.SnsIdentity
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.client.SnsTokenExchangerPort
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsRepositoryPort
import org.around.ieuri.auth.application.command.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.command.RequestPasswordResetCommand
import org.around.ieuri.auth.application.command.ResetPasswordCommand
import org.around.ieuri.auth.application.event.PasswordResetRequested
import org.around.ieuri.auth.application.port.out.persistence.EmailAuthentication
import org.around.ieuri.auth.application.port.out.persistence.MemberProfileRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.PasswordResetRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsView
import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.port.out.security.PasswordEncoderPort
import org.around.ieuri.auth.application.port.out.security.VerificationCodeGeneratorPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.aggregate.PasswordReset
import org.around.ieuri.auth.domain.event.EmailVerified
import org.around.ieuri.auth.domain.event.MemberRegistered
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.strategy.NicknameGenerator
import org.around.ieuri.auth.domain.vo.ProviderUserId
import kotlin.random.Random
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MemberRegistrationService — SNS 가입·이메일 검증 오케스트레이션 검증(MockK, Spring 없음). */
class MemberRegistrationServiceTest {

    private val snsVerifier = mockk<SnsIdentityVerifierPort>()
    private val memberRepository = mockk<MemberRepositoryPort>(relaxed = true)
    private val roleRepository = mockk<RoleRepositoryPort>(relaxed = true)
    private val termsRepository = mockk<TermsRepositoryPort>()
    private val verificationRepository = mockk<EmailVerificationRepositoryPort>(relaxed = true)
    private val codeGenerator = mockk<VerificationCodeGeneratorPort>()
    private val auditLog = mockk<AuditLogPort>(relaxed = true)
    private val eventPublisher = mockk<DomainEventPublisherPort>(relaxed = true)
    private val policy = VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60))
    private val passwordEncoder = mockk<PasswordEncoderPort>(relaxed = true)
    private val passwordResetRepository = mockk<PasswordResetRepositoryPort>(relaxed = true)
    private val refreshTokenStore = mockk<RefreshTokenStorePort>(relaxed = true)

    /** 보호 변환 스텁: 결정론적이고 평문과 구분되게 접두사를 붙인다(실제 HMAC은 어댑터 관심사). */
    private val identifierProtector = object : LoginIdentifierProtectorPort {
        override fun protect(normalizedIdentifier: String) = "protected:$normalizedIdentifier"
    }

    private val tokenExchanger = mockk<SnsTokenExchangerPort>()

    // 교환·저장 로직이 실제로 실행되도록 목이 아니라 실제 인스턴스를 조립한다.
    private val providerTokenCollector = ProviderTokenCollector(tokenExchanger, memberRepository)

    private val clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC)

    /** 가입 시 닉네임은 자동 생성된다(SPEC-0011 R20). 기본은 충돌 없음. */
    private val profileRepository = mockk<MemberProfileRepositoryPort> {
        every { existsByNicknameExcludingMember(any(), any()) } returns false
    }
    private val nicknameGenerator = NicknameGenerator(Random(20260807))

    private val service = MemberRegistrationService(
        snsVerifier, memberRepository, roleRepository, termsRepository, verificationRepository,
        passwordResetRepository, refreshTokenStore, codeGenerator, auditLog, eventPublisher, policy, passwordEncoder,
        profileRepository, nicknameGenerator, identifierProtector, providerTokenCollector, clock,
    )

    private val activeTerms = listOf(
        TermsView(1, "SERVICE", "1.0", required = true),
        TermsView(2, "PRIVACY", "1.0", required = true),
        TermsView(3, "MARKETING", "1.0", required = false),
    )

    private fun identity(email: String? = "user@example.com", verified: Boolean = true) =
        SnsIdentity("google-sub-1", email, verified, "여행자", null)

    private fun command(email: String = "user@example.com") = RegisterMemberCommand(
        provider = Provider.GOOGLE,
        idToken = "dev-token",
        submittedEmail = email,
        ageVerified = true,
        agreements = listOf(
            AgreementInput("SERVICE", true),
            AgreementInput("PRIVACY", true),
            AgreementInput("MARKETING", false),
        ),
    )

    // --- registerBySns ---

    @Test
    fun `제출 이메일이 provider 값과 같으면 검증 없이 가입 성공한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms
        every { memberRepository.save(any()) } returns 100L

        val result = service.registerBySns(command())

        assertTrue(result.emailVerified)
        assertNull(result.verificationPublicId)
        verify(exactly = 1) { memberRepository.save(any()) }
        verify { roleRepository.assignRoleToMember(100L, "USER") }
        verify(exactly = 0) { verificationRepository.save(any()) }
        verify(exactly = 0) { codeGenerator.generate() }
        verify { auditLog.record(100L, "MEMBER_REGISTERED", any()) }
    }

    @Test
    fun `사용자가 이메일을 수정하면 미검증으로 시작하고 검증 코드를 발급한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity(email = "original@example.com")
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms
        every { memberRepository.save(any()) } returns 100L
        every { codeGenerator.generate() } returns "123456"
        every { codeGenerator.hash("123456") } returns "hashed"

        val result = service.registerBySns(command(email = "edited@example.com"))

        assertFalse(result.emailVerified)
        assertNotNull(result.verificationPublicId)
        verify(exactly = 1) { verificationRepository.save(any<EmailVerification>()) }
        verify { eventPublisher.publish(any<VerificationEmailRequested>()) }
    }

    @Test
    fun `이미 가입된 계정이면 예외를 던진다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns true

        assertFailsWith<MemberAlreadyRegisteredException> { service.registerBySns(command()) }
        verify(exactly = 0) { memberRepository.save(any()) }
    }

    @Test
    fun `필수 약관 미동의면 도메인 불변식 위반으로 예외를 던진다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms

        val cmd = RegisterMemberCommand(
            provider = Provider.GOOGLE,
            idToken = "dev-token",
            submittedEmail = "user@example.com",
            ageVerified = true,
            agreements = listOf(AgreementInput("SERVICE", true)), // PRIVACY 미동의
        )
        assertFailsWith<IllegalArgumentException> { service.registerBySns(cmd) }
        verify(exactly = 0) { memberRepository.save(any()) }
    }

    @Test
    fun `SNS 식별자는 보호 변환 없이 provider가 준 값 그대로 저장한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms
        val saved = slot<Member>()
        every { memberRepository.save(capture(saved)) } returns 100L

        service.registerBySns(command())

        assertEquals("google-sub-1", saved.captured.providerUserId.value)
    }

    // --- registerBySns: provider 토큰 확보 (SPEC-0012 R5·R7) ---

    private fun givenSnsRegistrable() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms
        every { memberRepository.save(any()) } returns 100L
    }

    @Test
    fun `가입에 authorizationCode가 있으면 교환한 refresh token을 저장한다`() {
        givenSnsRegistrable()
        every { tokenExchanger.exchangeForRefreshToken(Provider.GOOGLE, "auth-code-1") } returns "provider-refresh-1"

        service.registerBySns(command().copy(authorizationCode = "auth-code-1"))

        verify { memberRepository.updateProviderRefreshToken(100L, "provider-refresh-1") }
    }

    @Test
    fun `가입에 authorizationCode가 없으면 교환하지 않는다`() {
        givenSnsRegistrable()

        service.registerBySns(command())

        verify(exactly = 0) { tokenExchanger.exchangeForRefreshToken(any(), any()) }
        verify(exactly = 0) { memberRepository.updateProviderRefreshToken(any(), any()) }
    }

    @Test
    fun `교환이 실패해도 가입은 성공한다`() {
        givenSnsRegistrable()
        every { tokenExchanger.exchangeForRefreshToken(any(), any()) } throws RuntimeException("provider 장애")

        val result = service.registerBySns(command().copy(authorizationCode = "auth-code-2"))

        assertTrue(result.emailVerified)
        verify(exactly = 1) { memberRepository.save(any()) }
        verify { auditLog.record(100L, "MEMBER_REGISTERED", any()) }
        verify(exactly = 0) { memberRepository.updateProviderRefreshToken(any(), any()) }
    }

    // --- registerByEmail ---

    private fun emailCommand(email: String = "user@example.com", password: String = "Passw0rd!") =
        RegisterEmailMemberCommand(
            email = email,
            password = password,
            ageVerified = true,
            agreements = listOf(
                AgreementInput("SERVICE", true),
                AgreementInput("PRIVACY", true),
            ),
        )

    @Test
    fun `이메일 가입은 미검증으로 시작하고 비밀번호 해시 저장·검증코드 발급한다`() {
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms
        every { passwordEncoder.encode("Passw0rd!") } returns "hashed-pw"
        every { memberRepository.save(any()) } returns 200L
        every { codeGenerator.generate() } returns "123456"
        every { codeGenerator.hash("123456") } returns "hashed-code"

        val result = service.registerByEmail(emailCommand())

        assertFalse(result.emailVerified)
        assertNotNull(result.verificationPublicId)
        verify { passwordEncoder.encode("Passw0rd!") }
        verify { roleRepository.assignRoleToMember(200L, "USER") }
        verify(exactly = 1) { verificationRepository.save(any<EmailVerification>()) }
        verify { eventPublisher.publish(any<VerificationEmailRequested>()) }
        verify { auditLog.record(200L, "MEMBER_REGISTERED", any()) }
    }

    @Test
    fun `이메일 가입은 평문 이메일이 아니라 보호된 값을 식별자로 저장한다`() {
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms
        every { passwordEncoder.encode(any()) } returns "hashed-pw"
        val saved = slot<Member>()
        every { memberRepository.save(capture(saved)) } returns 200L
        every { codeGenerator.generate() } returns "123456"
        every { codeGenerator.hash("123456") } returns "hashed-code"

        service.registerByEmail(emailCommand(email = "user@example.com"))

        val storedIdentifier = saved.captured.providerUserId.value
        assertEquals("protected:user@example.com", storedIdentifier)
        assertNotEquals("user@example.com", storedIdentifier)
        // 이메일 자체는 도메인에 평문으로 남는다(암호화는 영속 어댑터의 책임).
        assertEquals("user@example.com", saved.captured.email.value)
    }

    @Test
    fun `이메일 가입 중복 검사도 보호된 식별자로 한다`() {
        val probed = slot<ProviderUserId>()
        every { memberRepository.existsByProviderAndProviderUserId(Provider.EMAIL, capture(probed)) } returns true

        assertFailsWith<MemberAlreadyRegisteredException> { service.registerByEmail(emailCommand()) }

        assertEquals("protected:user@example.com", probed.captured.value)
    }

    @Test
    fun `이미 가입된 이메일이면 예외를 던진다`() {
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns true

        assertFailsWith<MemberAlreadyRegisteredException> { service.registerByEmail(emailCommand()) }
        verify(exactly = 0) { memberRepository.save(any()) }
    }

    @Test
    fun `비밀번호 정책 위반이면 커맨드 생성 단계에서 예외`() {
        // 특수문자 없음 → 정책 위반.
        assertFailsWith<IllegalArgumentException> { emailCommand(password = "Password1") }
    }

    // --- requestPasswordReset / resetPassword ---

    private fun emailAuth(passwordHash: String? = "hashed-pw") =
        EmailAuthentication(300L, MemberStatus.ACTIVE, null, null, passwordHash, true)

    private fun activeReset(codeHash: String = "reset-correct") = PasswordReset.issue(
        publicId = UUID.randomUUID(),
        memberId = 300L,
        codeHash = codeHash,
        ttl = Duration.ofMinutes(10),
        now = Instant.parse("2026-07-16T00:00:00Z"),
    )

    @Test
    fun `재설정 요청은 이메일 계정이면 코드를 발급하고 메일 이벤트를 발행한다`() {
        every { memberRepository.findEmailAuthentication("protected:user@example.com") } returns emailAuth()
        every { codeGenerator.generate() } returns "123456"
        every { codeGenerator.hash("123456") } returns "reset-hash"

        service.requestPasswordReset(RequestPasswordResetCommand("user@example.com"))

        verify { passwordResetRepository.consumeActiveByMemberId(300L) }
        verify { passwordResetRepository.save(any<PasswordReset>()) }
        verify { eventPublisher.publish(any<PasswordResetRequested>()) }
    }

    @Test
    fun `재설정 회원 조회는 평문이 아니라 보호된 식별자로 한다`() {
        val queried = slot<String>()
        every { memberRepository.findEmailAuthentication(capture(queried)) } returns emailAuth()
        every { codeGenerator.generate() } returns "123456"
        every { codeGenerator.hash("123456") } returns "reset-hash"

        service.requestPasswordReset(RequestPasswordResetCommand("  USER@Example.COM  "))

        // 정규화(Email VO) → 보호 변환 순서라 대소문자·공백이 달라도 같은 키로 회원을 찾는다.
        assertEquals("protected:user@example.com", queried.captured)
        verify { passwordResetRepository.save(any<PasswordReset>()) }
    }

    @Test
    fun `재설정 요청은 계정이 없거나 SNS 계정이면 조용히 무시한다(계정 열거 방지)`() {
        every { memberRepository.findEmailAuthentication(any()) } returns null

        service.requestPasswordReset(RequestPasswordResetCommand("ghost@example.com"))

        verify(exactly = 0) { passwordResetRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publish(any<PasswordResetRequested>()) }
    }

    @Test
    fun `재설정 실행은 코드가 맞으면 비밀번호 교체·세션 폐기·성공`() {
        every { memberRepository.findEmailAuthentication("protected:user@example.com") } returns emailAuth()
        every { passwordResetRepository.findActiveByMemberId(300L) } returns activeReset(codeHash = "reset-correct")
        every { codeGenerator.hash("123456") } returns "reset-correct"
        every { passwordEncoder.encode("NewPassw0rd!") } returns "new-hash"

        val result = service.resetPassword(ResetPasswordCommand("user@example.com", "123456", "NewPassw0rd!"))

        assertTrue(result.success)
        verify { memberRepository.updatePassword(300L, "new-hash") }
        verify { refreshTokenStore.revokeAllByMember(300L) }
        verify { auditLog.record(300L, "PASSWORD_RESET", any()) }
    }

    @Test
    fun `재설정 실행은 코드가 틀리면 실패 사유를 반환하고 비밀번호를 바꾸지 않는다`() {
        every { memberRepository.findEmailAuthentication("protected:user@example.com") } returns emailAuth()
        every { passwordResetRepository.findActiveByMemberId(300L) } returns activeReset(codeHash = "reset-correct")
        every { codeGenerator.hash("000000") } returns "reset-wrong"

        val result = service.resetPassword(ResetPasswordCommand("user@example.com", "000000", "NewPassw0rd!"))

        assertFalse(result.success)
        assertEquals("CODE_MISMATCH", result.failureReason)
        verify(exactly = 0) { memberRepository.updatePassword(any(), any()) }
    }

    @Test
    fun `재설정 실행은 활성 챌린지가 없으면 INVALID_OR_EXPIRED`() {
        every { memberRepository.findEmailAuthentication("protected:user@example.com") } returns emailAuth()
        every { passwordResetRepository.findActiveByMemberId(300L) } returns null

        val result = service.resetPassword(ResetPasswordCommand("user@example.com", "123456", "NewPassw0rd!"))

        assertFalse(result.success)
        assertEquals("INVALID_OR_EXPIRED", result.failureReason)
    }

    // --- verifyEmail ---

    private val verifyPublicId = UUID.randomUUID()

    private fun challenge(codeHash: String = "correct") = EmailVerification.issue(
        publicId = verifyPublicId,
        memberId = 100L,
        codeHash = codeHash,
        ttl = Duration.ofMinutes(10),
        now = Instant.parse("2026-07-16T00:00:00Z"),
    )

    @Test
    fun `올바른 코드면 검증 성공하고 회원을 검증 완료로 갱신한다`() {
        every { verificationRepository.findByPublicId(verifyPublicId) } returns challenge(codeHash = "correct")
        every { codeGenerator.hash("123456") } returns "correct"

        val result = service.verifyEmail(VerifyEmailCommand(verifyPublicId, "123456"))

        assertTrue(result.verified)
        verify { verificationRepository.update(any()) }
        verify { memberRepository.markEmailVerified(100L, any()) }
        verify { auditLog.record(100L, "EMAIL_VERIFIED", any()) }
        verify { eventPublisher.publish(any<EmailVerified>()) }
    }

    @Test
    fun `틀린 코드면 실패하고 회원을 갱신하지 않으며 attempts 반영을 위해 update한다`() {
        every { verificationRepository.findByPublicId(verifyPublicId) } returns challenge(codeHash = "correct")
        every { codeGenerator.hash("000000") } returns "wrong"

        val result = service.verifyEmail(VerifyEmailCommand(verifyPublicId, "000000"))

        assertFalse(result.verified)
        assertEquals("CODE_MISMATCH", result.failureReason)
        verify { verificationRepository.update(any()) }
        verify(exactly = 0) { memberRepository.markEmailVerified(any(), any()) }
    }

    @Test
    fun `핸들에 해당하는 챌린지가 없으면 예외를 던진다`() {
        every { verificationRepository.findByPublicId(verifyPublicId) } returns null

        assertFailsWith<VerificationNotFoundException> {
            service.verifyEmail(VerifyEmailCommand(verifyPublicId, "123456"))
        }
    }
}
