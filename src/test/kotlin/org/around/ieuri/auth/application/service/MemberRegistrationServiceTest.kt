package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
import org.around.ieuri.auth.application.exception.EmailAlreadyInUseException
import org.around.ieuri.auth.application.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.exception.NotEmailMemberException
import org.around.ieuri.auth.application.exception.PasswordPolicyViolationException
import org.around.ieuri.auth.application.exception.ResendCooldownActiveException
import org.around.ieuri.auth.application.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.around.ieuri.auth.application.port.out.client.SnsIdentity
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.client.SnsTokenExchangerPort
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.EmailAuthentication
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.PasswordResetRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsView
import org.around.ieuri.auth.application.port.out.security.EmailEncryptorPort
import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.port.out.security.PasswordEncoderPort
import org.around.ieuri.auth.application.port.out.security.VerificationCodeGeneratorPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.aggregate.PasswordReset
import org.around.ieuri.auth.domain.event.EmailVerified
import org.around.ieuri.auth.domain.strategy.NicknameGenerator
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.around.ieuri.auth.domain.vo.VerificationPurpose
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random
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

    /** 암호화 스텁: 되돌릴 수 있고 평문과 구분되게 접두사만 붙인다(실제 AES는 어댑터 관심사). */
    private val emailEncryptor = object : EmailEncryptorPort {
        override fun encrypt(plaintext: String) = "enc:$plaintext"
        override fun decrypt(ciphertext: String) = ciphertext.removePrefix("enc:")
    }

    private val tokenExchanger = mockk<SnsTokenExchangerPort>()

    // 교환·저장 로직이 실제로 실행되도록 목이 아니라 실제 인스턴스를 조립한다.
    private val providerTokenCollector = ProviderTokenCollector(tokenExchanger, memberRepository)

    // 중복 판정도 같은 이유로 실제 인스턴스다 — relaxed 목이 false를 주므로 기본은 "사용 가능".
    private val emailUniqueness = ProviderEmailUniqueness(memberRepository, identifierProtector)

    private val clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC)

    /** provider가 닉네임을 주지 않았을 때의 폴백 생성기(SPEC-0032 R30). */
    private val nicknameGenerator = NicknameGenerator(Random(20260807))

    private val service = MemberRegistrationService(
        snsVerifier, memberRepository, roleRepository, termsRepository, verificationRepository,
        passwordResetRepository, refreshTokenStore, codeGenerator, auditLog, eventPublisher, policy, passwordEncoder,
        nicknameGenerator, identifierProtector, emailEncryptor, providerTokenCollector,
        emailUniqueness, clock,
    )

    private val activeTerms = listOf(
        TermsView(1, "SERVICE", "1.0", required = true),
        TermsView(2, "PRIVACY", "1.0", required = true),
        TermsView(3, "MARKETING", "1.0", required = false),
    )

    private fun identity(email: String? = "user@example.com", nickname: String? = "여행자") = SnsIdentity("google-sub-1", email, nickname, null)

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

    // --- registerBySns: 닉네임 결정 (SPEC-0032 R29·R30) ---

    /** `save`에 실린 애그리거트에서 저장된 닉네임을 꺼낸다. */
    private fun registerAndCaptureNickname(
        providerNickname: String?,
        clientNickname: String? = null,
    ): String {
        val saved = slot<Member>()
        every { snsVerifier.verify(any(), any()) } returns identity(nickname = providerNickname)
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms
        every { memberRepository.save(capture(saved)) } returns 100L

        service.registerBySns(command().copy(nickname = clientNickname))

        return saved.captured.profile.nickname.value
    }

    @Test
    fun `provider가 준 닉네임을 그대로 쓴다`() {
        assertEquals("여행자", registerAndCaptureNickname("여행자"))
    }

    /** 클라이언트가 넘긴 값이 클레임보다 앞선다. */
    @Test
    fun `클라이언트가 넘긴 닉네임이 클레임보다 우선한다`() {
        val nickname = registerAndCaptureNickname(
            providerNickname = "클레임값",
            clientNickname = "클라이언트값",
        )

        assertEquals("클라이언트값", nickname)
    }

    @Test
    fun `클라이언트가 넘기지 않으면 클레임을 쓴다`() {
        assertEquals("클레임값", registerAndCaptureNickname("클레임값", clientNickname = null))
    }

    /** R30. provider 닉네임이 규칙(2~20자)을 벗어나도 오류가 아니라 다음 후보로 넘어간다. */
    @Test
    fun `클라이언트 닉네임이 한 글자여도 오류가 아니라 다음 후보로 넘어간다`() {
        val nickname = registerAndCaptureNickname(
            providerNickname = "클레임값",
            clientNickname = "가",
        )

        // 1글자는 규칙 위반이라 버려지고 클레임이 받는다.
        assertEquals("클레임값", nickname)
    }

    @Test
    fun `클라이언트와 클레임이 모두 규칙을 벗어나면 생성기로 물러난다`() {
        val nickname = registerAndCaptureNickname(
            providerNickname = "가".repeat(21),
            clientNickname = "나",
        )

        assertEquals(nickname, Nickname(nickname).value, "생성값이 닉네임 규칙을 벗어났다: $nickname")
        assertTrue(nickname != "나")
    }

    /** 클레임에 닉네임이 실릴지는 동의 항목 설정에 달려 있어 서버가 통제하지 못한다. */
    @Test
    fun `provider 닉네임이 없으면 생성기로 물러난다`() {
        val nickname = registerAndCaptureNickname(null)

        assertEquals(nickname, Nickname(nickname).value, "생성값이 닉네임 규칙을 벗어났다: $nickname")
        assertTrue(nickname != "여행자")
    }

    /** R30. 잘라내지 않는다 — 범위를 벗어나면 통째로 생성기로 물러난다. */
    @Test
    fun `provider 닉네임이 길이 규칙을 벗어나면 자르지 않고 생성기로 물러난다`() {
        listOf("가", "가".repeat(21)).forEach { raw ->
            val nickname = registerAndCaptureNickname(raw)

            assertEquals(nickname, Nickname(nickname).value)
            assertTrue(!nickname.startsWith(raw), "provider 값을 잘라 썼다: $nickname")
        }
    }

    /** 공백만 있는 값도 규칙을 만족하지 않으므로 폴백 대상이다. */
    @Test
    fun `provider 닉네임이 공백뿐이면 생성기로 물러난다`() {
        val nickname = registerAndCaptureNickname("   ")

        assertEquals(nickname, Nickname(nickname).value)
    }

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

    /** R12-1·R12-2. 제출값이 provider 값과 달라도 provider 값이 저장되고 인증은 면제된다. */
    @Test
    fun `제출 이메일이 provider 값과 달라도 provider 값을 저장하고 검증을 면제한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity(email = "original@example.com")
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { memberRepository.existsByProviderAndEmailIdentifier(any(), any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms
        every { memberRepository.save(any()) } returns 100L

        val result = service.registerBySns(command(email = "edited@example.com"))

        assertTrue(result.emailVerified)
        assertNull(result.verificationPublicId)
        // 저장된 이메일이 제출값이 아니라 provider 값인지 본다.
        val saved = slot<Member>()
        verify { memberRepository.save(capture(saved)) }
        assertEquals("original@example.com", saved.captured.email.value)
        verify(exactly = 0) { verificationRepository.save(any<EmailVerification>()) }
    }

    /** R3-1·R12-3. provider가 이메일을 주지 않으면 제출값을 쓰고 인증 절차를 밟는다. */
    @Test
    fun `provider가 이메일을 주지 않으면 제출값으로 미검증 가입하고 검증 코드를 발급한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity(email = null)
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { memberRepository.existsByProviderAndEmailIdentifier(any(), any(), any()) } returns false
        every { termsRepository.findActiveTerms(any()) } returns activeTerms
        every { memberRepository.save(any()) } returns 100L
        every { codeGenerator.generate() } returns "123456"
        every { codeGenerator.hash("123456") } returns "hashed"

        val result = service.registerBySns(command(email = "typed@example.com"))

        assertFalse(result.emailVerified)
        assertNotNull(result.verificationPublicId)
        val saved = slot<Member>()
        verify { memberRepository.save(capture(saved)) }
        assertEquals("typed@example.com", saved.captured.email.value)
        verify(exactly = 1) { verificationRepository.save(any<EmailVerification>()) }
        verify { eventPublisher.publish(any<VerificationEmailRequested>()) }
    }

    /** R5-1. 같은 provider에 같은 주소가 있으면 거부한다. */
    @Test
    fun `같은 provider에 같은 이메일이 있으면 가입을 거부한다`() {
        every { snsVerifier.verify(any(), any()) } returns identity()
        every { memberRepository.existsByProviderAndProviderUserId(any(), any()) } returns false
        every { memberRepository.existsByProviderAndEmailIdentifier(any(), any(), any()) } returns true
        every { termsRepository.findActiveTerms(any()) } returns activeTerms

        val ex = assertFailsWith<EmailAlreadyInUseException> { service.registerBySns(command()) }

        // R5-4 — 상대 회원의 정보가 메시지에 담기지 않는다(POL-013).
        assertEquals("EMAIL_ALREADY_IN_USE", ex.code)
        assertFalse(ex.message!!.contains("@"))
        verify(exactly = 0) { memberRepository.save(any()) }
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

    private fun emailCommand(email: String = "user@example.com", password: String = "Passw0rd!") = RegisterEmailMemberCommand(
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
    fun `비밀번호 정책 위반은 전용 코드로 나간다`() {
        // 특수문자 없음 → INVALID_INPUT이 아니라 전용 코드다(POL-022).
        val ex = assertFailsWith<PasswordPolicyViolationException> { emailCommand(password = "Password1") }
        assertEquals("PASSWORD_POLICY_VIOLATION", ex.code)
    }

    // --- requestPasswordReset / resetPassword ---

    private fun emailAuth(passwordHash: String? = "hashed-pw") = EmailAuthentication(300L, MemberStatus.ACTIVE, null, null, passwordHash, true)

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

    /** 가입되지 않은 주소는 코드로 거부한다. */
    @Test
    fun `재설정 요청은 가입되지 않은 주소면 MEMBER_NOT_FOUND로 거부한다`() {
        every { memberRepository.findEmailAuthentication(any()) } returns null

        val ex = assertFailsWith<MemberNotFoundException> {
            service.requestPasswordReset(RequestPasswordResetCommand("ghost@example.com"))
        }

        assertEquals("MEMBER_NOT_FOUND", ex.code)
        verify(exactly = 0) { passwordResetRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publish(any<PasswordResetRequested>()) }
    }

    /** SNS 계정은 재설정할 비밀번호가 없어 성립하지 않는다. 발송하지 않고 코드로 알린다. */
    @Test
    fun `재설정 요청은 SNS 계정이면 NOT_EMAIL_MEMBER로 거부한다`() {
        every { memberRepository.findEmailAuthentication(any()) } returns emailAuth(passwordHash = null)

        val ex = assertFailsWith<NotEmailMemberException> {
            service.requestPasswordReset(RequestPasswordResetCommand("sns@example.com"))
        }

        assertEquals("NOT_EMAIL_MEMBER", ex.code)
        verify(exactly = 0) { passwordResetRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publish(any<PasswordResetRequested>()) }
    }

    /** 발송 쿨다운. 없으면 주소를 아는 제3자가 메일함에 코드를 무제한 투하할 수 있다. */
    @Test
    fun `재설정 요청은 쿨다운 이내면 RESEND_COOLDOWN_ACTIVE로 거부한다`() {
        every { memberRepository.findEmailAuthentication(any()) } returns emailAuth()
        every { passwordResetRepository.findLatestCreatedAtByMemberId(any()) } returns
            clock.instant().minusSeconds(30)

        assertFailsWith<ResendCooldownActiveException> {
            service.requestPasswordReset(RequestPasswordResetCommand("user@example.com"))
        }

        verify(exactly = 0) { passwordResetRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publish(any<PasswordResetRequested>()) }
    }

    /** 같은 이유로 실행 단계도 막힌다 — 코드 대조 자체에 도달하지 않는다. */
    @Test
    fun `재설정 실행은 SNS 계정이면 코드 오타와 같은 사유로 끝난다`() {
        every { memberRepository.findEmailAuthentication(any()) } returns emailAuth(passwordHash = null)

        val result = service.resetPassword(
            ResetPasswordCommand("sns@example.com", "123456", "NewPass1!"),
        )

        assertFalse(result.success)
        // 2단계는 사유를 통일한다 — 여기서 갈리면 코드를 모르는 제3자가 열거할 수 있다.
        assertEquals("CODE_MISMATCH", result.failureReason)
        verify(exactly = 0) { passwordResetRepository.findActiveByMemberId(any()) }
        verify(exactly = 0) { memberRepository.updatePassword(any(), any()) }
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
    fun `재설정 실행은 활성 챌린지가 없으면 코드 오타와 같은 사유로 끝난다`() {
        every { memberRepository.findEmailAuthentication("protected:user@example.com") } returns emailAuth()
        every { passwordResetRepository.findActiveByMemberId(300L) } returns null

        val result = service.resetPassword(ResetPasswordCommand("user@example.com", "123456", "NewPassw0rd!"))

        assertFalse(result.success)
        // 2단계는 사유를 통일한다 — 여기서 갈리면 코드를 모르는 제3자가 열거할 수 있다.
        assertEquals("CODE_MISMATCH", result.failureReason)
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

    // --- 인증 주소 재입력 (SPEC-0024 R7) ---

    /** 미인증 회원을 하나 세운다. `load`가 이 회원을 돌려주도록 스텁한다. */
    private fun pendingMember(
        provider: Provider = Provider.EMAIL,
        email: String = "wrong@example.com",
        emailVerified: Boolean = false,
    ): Member = Member.reconstitute(
        id = 100L,
        provider = provider,
        providerUserId = ProviderUserId("pid-1"),
        email = Email(email),
        emailVerified = emailVerified,
        status = MemberStatus.ACTIVE,
        ageVerified = true,
        suspendedAt = null,
        suspendedUntil = null,
        suspensionReason = null,
        nickname = Nickname("여행자"),
        profileImageUrl = null,
        createdAt = Instant.parse("2026-07-01T00:00:00Z"),
    )

    /** R7-1. 가입 인증 목적만 무효화하고 새 주소로 코드를 낸다. */
    @Test
    fun `주소를 정정하면 가입 인증 챌린지만 폐기하고 새 주소로 코드를 발급한다`() {
        every { memberRepository.load(100L) } returns pendingMember()
        every { memberRepository.existsByProviderAndEmailIdentifier(any(), any(), any()) } returns false
        every { verificationRepository.findLatestCreatedAtByMemberIdAndPurpose(any(), any()) } returns null
        every { codeGenerator.generate() } returns "123456"
        every { codeGenerator.hash("123456") } returns "hashed"

        val result = service.retargetEmailVerification(
            RetargetEmailVerificationCommand(100L, "right@example.com"),
        )

        assertNotNull(result.verificationPublicId)
        // 폐기 대상이 REGISTRATION 목적으로 한정된다 — 진행 중인 이메일 변경 검증은 건드리지 않는다.
        verify(exactly = 1) {
            verificationRepository.consumeActiveByMemberIdAndPurpose(100L, VerificationPurpose.REGISTRATION, any())
        }
        verify(exactly = 0) {
            verificationRepository.consumeActiveByMemberIdAndPurpose(100L, VerificationPurpose.EMAIL_CHANGE, any())
        }
        // **아직 회원의 이메일을 바꾸지 않는다** — 소유를 증명하기 전에 유니크를 선점시키지 않는다.
        verify(exactly = 0) { memberRepository.updateEmail(any(), any(), any(), any()) }
        // 새 주소는 챌린지가 암호문으로 들고 있다가 verifyEmail 통과 시 확정된다.
        val issued = slot<EmailVerification>()
        verify { verificationRepository.save(capture(issued)) }
        assertEquals("enc:right@example.com", issued.captured.targetEmailEncrypted)
        // 코드는 새 주소로 간다. 포트가 `publish(event: Any)`라 슬롯도 Any로 받는다.
        val sent = slot<Any>()
        verify { eventPublisher.publish(capture(sent)) }
        assertEquals("right@example.com", (sent.captured as VerificationEmailRequested).toEmail)
    }

    /** R7-2. INVALID_INPUT이 아니라 전용 코드로 거부한다 — 클라이언트는 인증 화면을 벗어나야 한다. */
    @Test
    fun `이미 인증을 마친 회원의 주소 정정은 ALREADY_VERIFIED로 거부된다`() {
        every { memberRepository.load(100L) } returns pendingMember(emailVerified = true)

        val ex = assertFailsWith<AlreadyVerifiedException> {
            service.retargetEmailVerification(RetargetEmailVerificationCommand(100L, "other@example.com"))
        }
        assertEquals("ALREADY_VERIFIED", ex.code)
        verify(exactly = 0) { memberRepository.updateEmail(any(), any(), any(), any()) }
        verify(exactly = 0) { verificationRepository.save(any()) }
    }

    /** R7-3. 쿨다운 창을 resendVerification과 공유한다 — 같은 목적으로 센다. */
    @Test
    fun `쿨다운 이내면 거부하고 resendVerification과 같은 창을 쓴다`() {
        every { memberRepository.load(100L) } returns pendingMember()
        every { memberRepository.existsByProviderAndEmailIdentifier(any(), any(), any()) } returns false
        every {
            verificationRepository.findLatestCreatedAtByMemberIdAndPurpose(100L, VerificationPurpose.REGISTRATION)
        } returns clock.instant().minusSeconds(30)

        // 계약이 이 오퍼레이션에 지정한 코드다 — 재발송과 창은 공유하되 코드는 나뉜다.
        assertFailsWith<ResendCooldownActiveException> {
            service.retargetEmailVerification(RetargetEmailVerificationCommand(100L, "right@example.com"))
        }
        verify(exactly = 0) { verificationRepository.save(any()) }
    }

    /** R7-4. 같은 provider에 이미 있는 주소로는 정정할 수 없고, 상대 정보는 새지 않는다. */
    @Test
    fun `같은 provider에 이미 있는 주소로 정정하면 거부한다`() {
        every { memberRepository.load(100L) } returns pendingMember(provider = Provider.KAKAO)
        every { memberRepository.existsByProviderAndEmailIdentifier(Provider.KAKAO, any(), 100L) } returns true

        val ex = assertFailsWith<EmailAlreadyInUseException> {
            service.retargetEmailVerification(RetargetEmailVerificationCommand(100L, "taken@example.com"))
        }

        assertEquals("EMAIL_ALREADY_IN_USE", ex.code)
        assertFalse(ex.message!!.contains("@"))
        verify(exactly = 0) { memberRepository.updateEmail(any(), any(), any(), any()) }
    }

    /** 정정한 주소를 담은 챌린지. verifyEmail이 통과시키면 이 주소가 회원에게 확정된다. */
    private fun retargetChallenge(target: String = "right@example.com") = EmailVerification.issue(
        publicId = verifyPublicId,
        memberId = 100L,
        codeHash = "correct",
        ttl = java.time.Duration.ofMinutes(10),
        now = clock.instant(),
        maxAttempts = 5,
        targetEmailEncrypted = "enc:$target",
    )

    /** R7-5. 정정된 주소는 **코드 검증을 통과할 때** 회원에게 확정된다. */
    @Test
    fun `정정한 주소는 인증을 통과할 때 회원에게 확정된다`() {
        every { verificationRepository.findByPublicId(verifyPublicId) } returns retargetChallenge()
        every { codeGenerator.hash("123456") } returns "correct"
        every { memberRepository.load(100L) } returns pendingMember(provider = Provider.KAKAO)
        every { memberRepository.existsByProviderAndEmailIdentifier(any(), any(), any()) } returns false

        val result = service.verifyEmail(VerifyEmailCommand(verifyPublicId, "123456"))

        assertTrue(result.verified)
        verify { memberRepository.updateEmail(100L, Email("right@example.com"), any(), any()) }
        verify { memberRepository.markEmailVerified(100L, any()) }
    }

    /** SNS 회원은 provider_user_id가 provider가 준 값이라 건드리지 않는다. */
    @Test
    fun `SNS 회원의 주소 확정은 로그인 식별자를 바꾸지 않는다`() {
        every { verificationRepository.findByPublicId(verifyPublicId) } returns retargetChallenge()
        every { codeGenerator.hash("123456") } returns "correct"
        every { memberRepository.load(100L) } returns pendingMember(provider = Provider.KAKAO)
        every { memberRepository.existsByProviderAndEmailIdentifier(any(), any(), any()) } returns false

        service.verifyEmail(VerifyEmailCommand(verifyPublicId, "123456"))

        verify { memberRepository.updateEmail(100L, any(), any(), null) }
    }

    /** EMAIL 회원은 이메일이 곧 식별자라 함께 바뀌어야 다음 로그인이 성립한다. */
    @Test
    fun `EMAIL 회원의 주소 확정은 로그인 식별자도 함께 바꾼다`() {
        every { verificationRepository.findByPublicId(verifyPublicId) } returns retargetChallenge()
        every { codeGenerator.hash("123456") } returns "correct"
        every { memberRepository.load(100L) } returns pendingMember(provider = Provider.EMAIL)
        every { memberRepository.existsByProviderAndEmailIdentifier(any(), any(), any()) } returns false

        service.verifyEmail(VerifyEmailCommand(verifyPublicId, "123456"))

        verify { memberRepository.updateEmail(100L, any(), any(), "protected:right@example.com") }
    }

    /** 주소를 고친 뒤 재발송하면 정정한 주소로 다시 보낸다. 회원 행을 읽으면 정정이 조용히 취소된다. */
    @Test
    fun `정정 후 재발송은 정정한 주소를 이어받는다`() {
        every { verificationRepository.findByPublicId(verifyPublicId) } returns retargetChallenge()
        every { verificationRepository.findLatestCreatedAtByMemberIdAndPurpose(any(), any()) } returns null
        every { codeGenerator.generate() } returns "123456"
        every { codeGenerator.hash("123456") } returns "hashed"

        service.resendVerification(ResendVerificationCommand(verifyPublicId))

        // 회원 행(정정 이전 주소)을 읽지 않는다.
        verify(exactly = 0) { memberRepository.findEmailByMemberId(any()) }
        val sent = slot<Any>()
        verify { eventPublisher.publish(capture(sent)) }
        assertEquals("right@example.com", (sent.captured as VerificationEmailRequested).toEmail)
        // 대기 주소가 새 챌린지로 이어져야 이후 verifyEmail이 확정할 수 있다.
        val issued = slot<EmailVerification>()
        verify { verificationRepository.save(capture(issued)) }
        assertEquals("enc:right@example.com", issued.captured.targetEmailEncrypted)
    }

    /** 이메일 변경 목적으로 발급된 챌린지. `verifyEmail`·`resendVerification`이 다루면 안 된다. */
    private fun emailChangeChallenge() = EmailVerification.issue(
        publicId = verifyPublicId,
        memberId = 100L,
        codeHash = "correct",
        ttl = java.time.Duration.ofMinutes(10),
        now = clock.instant(),
        maxAttempts = 5,
        purpose = VerificationPurpose.EMAIL_CHANGE,
        targetEmailEncrypted = "enc:victim@example.com",
    )

    /** 이메일 변경 핸들로 가입 인증을 통과시킬 수 없다. 거르지 않으면 인증 없는 경로로 남의 이메일 변경이 확정된다. */
    @Test
    fun `이메일 변경 핸들로는 가입 인증을 할 수 없다`() {
        every { verificationRepository.findByPublicId(verifyPublicId) } returns emailChangeChallenge()

        assertFailsWith<VerificationNotFoundException> {
            service.verifyEmail(VerifyEmailCommand(verifyPublicId, "123456"))
        }
        verify(exactly = 0) { memberRepository.updateEmail(any(), any(), any(), any()) }
        verify(exactly = 0) { memberRepository.markEmailVerified(any(), any()) }
    }

    /** 재발송도 같다 — 거르지 않으면 변경 대상 주소로 가입 인증 코드를 보내게 된다. */
    @Test
    fun `이메일 변경 핸들로는 가입 인증 코드를 재발송할 수 없다`() {
        every { verificationRepository.findByPublicId(verifyPublicId) } returns emailChangeChallenge()

        assertFailsWith<VerificationNotFoundException> {
            service.resendVerification(ResendVerificationCommand(verifyPublicId))
        }
        verify(exactly = 0) { verificationRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publish(any<VerificationEmailRequested>()) }
    }

    /** 정정하지 않은 보통 가입 챌린지는 이메일을 건드리지 않는다. */
    @Test
    fun `대상 주소가 없는 챌린지는 이메일을 바꾸지 않는다`() {
        every { verificationRepository.findByPublicId(verifyPublicId) } returns challenge(codeHash = "correct")
        every { codeGenerator.hash("123456") } returns "correct"

        service.verifyEmail(VerifyEmailCommand(verifyPublicId, "123456"))

        verify(exactly = 0) { memberRepository.updateEmail(any(), any(), any(), any()) }
        verify { memberRepository.markEmailVerified(any(), any()) }
    }
}
