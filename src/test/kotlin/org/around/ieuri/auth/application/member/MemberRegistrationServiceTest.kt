package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.dto.AgreementInput
import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterSnsIdentityCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand
import org.around.ieuri.auth.application.member.provided.exception.EmailAlreadyInUseException
import org.around.ieuri.auth.application.member.provided.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.provided.exception.PasswordPolicyViolationException
import org.around.ieuri.auth.application.member.provided.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.application.member.required.PasswordEncoderPort
import org.around.ieuri.auth.application.member.required.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.member.Agreement
import org.around.ieuri.auth.domain.member.AlreadyVerifiedException
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.EmailVerification
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.MemberRoleChange
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.NicknameGenerator
import org.around.ieuri.auth.domain.member.Profile
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.auth.domain.member.RequiredTermsNotAgreedException
import org.around.ieuri.auth.domain.member.ResendCooldownActiveException
import org.around.ieuri.auth.domain.member.Role
import org.around.ieuri.auth.domain.member.Terms
import org.around.ieuri.auth.domain.member.UnderageRegistrationException
import org.around.ieuri.auth.domain.member.VerificationPolicy
import org.around.ieuri.auth.domain.member.VerificationPurpose
import org.around.ieuri.auth.domain.member.VerificationRateLimitedException
import org.around.ieuri.auth.domain.member.event.EmailVerificationRetargeted
import org.around.ieuri.auth.domain.member.event.EmailVerified
import org.around.ieuri.auth.domain.member.event.MemberRegistered
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 가입과 가입 이메일 인증(SPEC-0024 R2~R7·R12, SPEC-0032 R29~R31).
 * - 판정 순서가 곧 오류 코드의 우선순위라 순서를 고정한다.
 * - 평문 코드는 이벤트로만 나가고, 저장되는 것은 해시다.
 */
class MemberRegistrationServiceTest {

    private val now = Instant.parse("2026-07-16T00:00:00Z")
    private val policy = VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60))
    private val nicknameSeed = 20260807

    private val memberRepository = mockk<MemberRepository>(relaxUnitFun = true)
    private val codeGenerator = mockk<VerificationCodeGeneratorPort> {
        every { generate() } returns "123456"
        every { hash(any()) } answers { "hashed:${firstArg<String>()}" }
    }
    private val passwordEncoder = mockk<PasswordEncoderPort> {
        every { encode(any()) } answers { "encoded:${firstArg<String>()}" }
    }
    private val eventPublisher = mockk<MemberEventPublisherPort>()
    private val events = mutableListOf<Any>()
    private val identifierProtector = prefixingIdentifierProtector()

    // 중복 판정은 실제 인스턴스다 — 어느 주소·provider로 묻는지까지 함께 고정된다.
    private val emailUniqueness = ProviderEmailUniqueness(memberRepository, identifierProtector)

    private val service = MemberRegistrationService(
        memberRepository,
        codeGenerator,
        eventPublisher,
        policy,
        passwordEncoder,
        NicknameGenerator(Random(nicknameSeed)),
        identifierProtector,
        emailUniqueness,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private val serviceTerms = Terms(id = 1, code = "SERVICE", version = "1.0", required = true)
    private val privacyTerms = Terms(id = 2, code = "PRIVACY", version = "1.1", required = true)
    private val marketingTerms = Terms(id = 3, code = "MARKETING", version = "1.2", required = false)
    private val userRole = Role(id = 10, code = "USER", name = "일반", permissions = emptySet())

    private val insertedMember = slot<Member>()
    private val insertedProfile = slot<Profile>()
    private val insertedAgreements = slot<List<Agreement>>()
    private val insertedVerification = slot<EmailVerification>()

    init {
        every { eventPublisher.publish(capture(events)) } just runs
        every { memberRepository.existsByProviderUserId(any(), any()) } returns false
        every { memberRepository.existsByEmailIdentifier(any(), any(), any()) } returns false
        every { memberRepository.findEffectiveTerms(now) } returns listOf(serviceTerms, privacyTerms, marketingTerms)
        every { memberRepository.insert(capture(insertedMember), capture(insertedProfile), capture(insertedAgreements)) } returns 100L
        every { memberRepository.findRole(Role.USER) } returns userRole
        every { memberRepository.insertEmailVerification(capture(insertedVerification)) } just runs
        every { memberRepository.findEmailVerifications(any(), any()) } returns emptyList()
    }

    private val requiredAgreed = listOf(
        AgreementInput("SERVICE", true),
        AgreementInput("PRIVACY", true),
    )

    private fun snsCommand(
        submittedEmail: String = "user@example.com",
        providerEmail: String? = "user@example.com",
        clientNickname: String? = null,
        providerNickname: String? = "여행자",
        ageVerified: Boolean = true,
        agreements: List<AgreementInput> = requiredAgreed,
    ) = RegisterSnsIdentityCommand(
        request = RegisterMemberCommand(
            provider = Provider.GOOGLE,
            idToken = "id-token",
            submittedEmail = submittedEmail,
            ageVerified = ageVerified,
            agreements = agreements,
            nickname = clientNickname,
        ),
        providerUserId = "google-sub-1",
        email = providerEmail,
        nickname = providerNickname,
        profileImageUrl = "https://img.example/p.png",
    )

    private fun emailCommand(
        email: String = "user@example.com",
        ageVerified: Boolean = true,
        agreements: List<AgreementInput> = requiredAgreed,
    ) = RegisterEmailMemberCommand(email = email, password = "Passw0rd!", ageVerified = ageVerified, agreements = agreements)

    // --- SNS 가입 ---

    /** R2. provider가 이메일을 줬으면 그 주소가 저장되고 인증은 면제된다 — 코드를 만들지 않는다. */
    @Test
    fun `provider가 이메일을 주면 인증된 회원으로 가입하고 코드를 발급하지 않는다`() {
        val result = service.registerBySnsIdentity(snsCommand())

        assertEquals(100L, result.memberId)
        assertTrue(result.result.emailVerified)
        assertNull(result.result.verificationPublicId)
        assertNull(insertedMember.captured.id)
        assertEquals(Provider.GOOGLE, insertedMember.captured.provider)
        assertEquals("google-sub-1", insertedMember.captured.providerUserId.value)
        assertEquals("user@example.com", insertedMember.captured.email.value)
        assertTrue(insertedMember.captured.emailVerified)
        assertEquals(MemberStatus.ACTIVE, insertedMember.captured.status)
        assertNull(insertedMember.captured.passwordHash)
        verify(exactly = 0) { codeGenerator.generate() }
        verify(exactly = 0) { memberRepository.insertEmailVerification(any()) }
        assertEquals(listOf<Any>(MemberRegistered(100L, Provider.GOOGLE, emailVerified = true, verificationPublicId = null, occurredAt = now)), events)
    }

    /** R12. 제출값이 provider 값과 달라도 provider 값이 저장되고, 중복도 저장될 주소로 판정한다. */
    @Test
    fun `제출 이메일이 provider 값과 달라도 provider 값을 저장하고 그 주소로 중복을 본다`() {
        val result = service.registerBySnsIdentity(snsCommand(submittedEmail = "edited@example.com", providerEmail = "original@example.com"))

        assertTrue(result.result.emailVerified)
        assertEquals("original@example.com", insertedMember.captured.email.value)
        verify(exactly = 1) { memberRepository.existsByEmailIdentifier(Provider.GOOGLE, "protected:original@example.com", null) }
        verify(exactly = 0) { memberRepository.existsByEmailIdentifier(any(), "protected:edited@example.com", any()) }
    }

    /** R3. provider가 이메일을 주지 않으면 제출값을 쓰고, 코드는 저장된 주소로 보낸다. */
    @Test
    fun `provider가 이메일을 주지 않으면 제출값으로 미인증 가입하고 저장된 주소로 코드를 보낸다`() {
        val result = service.registerBySnsIdentity(snsCommand(submittedEmail = "  Typed@Example.com ", providerEmail = null))

        assertFalse(result.result.emailVerified)
        assertEquals("typed@example.com", insertedMember.captured.email.value)
        assertFalse(insertedMember.captured.emailVerified)

        val issued = insertedVerification.captured
        assertEquals(issued.publicId, result.result.verificationPublicId)
        assertEquals(100L, issued.memberId)
        assertEquals("hashed:123456", issued.codeHash)
        assertEquals(now.plus(policy.ttl), issued.expiresAt)
        assertEquals(policy.maxAttempts, issued.maxAttempts)
        assertEquals(VerificationPurpose.REGISTRATION, issued.purpose)
        assertNull(issued.targetEmail)
        assertEquals(
            listOf(
                VerificationEmailRequested("typed@example.com", "123456"),
                MemberRegistered(100L, Provider.GOOGLE, emailVerified = false, verificationPublicId = issued.publicId, occurredAt = now),
            ),
            events,
        )
    }

    @Test
    fun `이미 가입된 SNS 계정이면 거부하고 아무것도 넣지 않는다`() {
        every { memberRepository.existsByProviderUserId(Provider.GOOGLE, ProviderUserId("google-sub-1")) } returns true

        val ex = assertFailsWith<MemberAlreadyRegisteredException> { service.registerBySnsIdentity(snsCommand()) }

        assertEquals("이미 가입된 계정입니다.", ex.message)
        verify(exactly = 0) { memberRepository.insert(any(), any(), any()) }
        assertTrue(events.isEmpty())
    }

    /** R5. 같은 provider에 같은 주소가 있으면 거부한다. 메시지에 상대 정보가 없다(POL-013). */
    @Test
    fun `같은 provider에 같은 이메일이 있으면 SNS 가입을 거부한다`() {
        every { memberRepository.existsByEmailIdentifier(Provider.GOOGLE, "protected:user@example.com", null) } returns true

        val ex = assertFailsWith<EmailAlreadyInUseException> { service.registerBySnsIdentity(snsCommand()) }

        assertEquals("이미 사용 중인 이메일입니다.", ex.message)
        verify(exactly = 0) { memberRepository.insert(any(), any(), any()) }
        assertTrue(events.isEmpty())
    }

    /** SNS 가입은 가입 자격(만 14세·필수 약관)을 이메일 중복보다 먼저 본다. */
    @Test
    fun `SNS 가입은 자격 미달을 이메일 중복보다 먼저 답한다`() {
        every { memberRepository.existsByEmailIdentifier(any(), any(), any()) } returns true

        val underage = assertFailsWith<UnderageRegistrationException> { service.registerBySnsIdentity(snsCommand(ageVerified = false)) }
        val missingTerms = assertFailsWith<RequiredTermsNotAgreedException> {
            service.registerBySnsIdentity(snsCommand(agreements = listOf(AgreementInput("SERVICE", true), AgreementInput("PRIVACY", false))))
        }

        assertEquals("만 14세 미만은 가입할 수 없습니다.", underage.message)
        assertEquals("필수 약관에 동의해야 가입할 수 있습니다. 미동의: [PRIVACY]", missingTerms.message)
        verify(exactly = 0) { memberRepository.existsByEmailIdentifier(any(), any(), any()) }
        verify(exactly = 0) { memberRepository.insert(any(), any(), any()) }
    }

    /** 제출하지 않은 약관은 미동의로 남기고, 시행 중인 약관 전부를 그 버전으로 기록한다. */
    @Test
    fun `가입 동의는 시행 중인 약관 전부에 대해 저장한다`() {
        service.registerBySnsIdentity(snsCommand())

        assertEquals(
            listOf(
                Agreement(termsId = 1, termsCode = "SERVICE", termsVersion = "1.0", agreed = true, agreedAt = now),
                Agreement(termsId = 2, termsCode = "PRIVACY", termsVersion = "1.1", agreed = true, agreedAt = now),
                Agreement(termsId = 3, termsCode = "MARKETING", termsVersion = "1.2", agreed = false, agreedAt = now),
            ),
            insertedAgreements.captured,
        )
    }

    @Test
    fun `기본 역할 USER가 있으면 가입한 회원에게 부여한다`() {
        service.registerBySnsIdentity(snsCommand())

        verifyOrder {
            memberRepository.insert(any(), any(), any())
            memberRepository.addRole(MemberRoleChange.Granted(100L, userRole), now)
        }
    }

    @Test
    fun `기본 역할이 시드에 없으면 역할 없이 가입한다`() {
        every { memberRepository.findRole(Role.USER) } returns null

        val result = service.registerBySnsIdentity(snsCommand())

        assertEquals(100L, result.memberId)
        verify(exactly = 0) { memberRepository.addRole(any(), any()) }
    }

    // --- SNS 가입: 닉네임 결정 (SPEC-0032 R29·R30) ---

    private fun registeredNickname(clientNickname: String?, providerNickname: String?): String {
        service.registerBySnsIdentity(snsCommand(clientNickname = clientNickname, providerNickname = providerNickname))
        return insertedProfile.captured.nickname.value
    }

    /** 같은 시드의 생성기가 처음 내는 값 — 서비스가 생성기로 물러났는지를 값으로 확인한다. */
    private fun generatedNickname(): String = NicknameGenerator(Random(nicknameSeed)).generate().value

    @Test
    fun `클라이언트가 넘긴 닉네임이 클레임보다 우선한다`() {
        assertEquals("클라이언트값", registeredNickname(clientNickname = "클라이언트값", providerNickname = "클레임값"))
        assertEquals("https://img.example/p.png", insertedProfile.captured.profileImageUrl)
    }

    @Test
    fun `클라이언트가 넘기지 않으면 클레임을 쓴다`() {
        assertEquals("클레임값", registeredNickname(clientNickname = null, providerNickname = "  클레임값  "))
    }

    /** R30. 규칙을 벗어난 후보는 오류가 아니라 통째로 건너뛴다. */
    @Test
    fun `클라이언트 닉네임이 규칙을 벗어나면 오류 없이 클레임으로 넘어간다`() {
        assertEquals("클레임값", registeredNickname(clientNickname = "가", providerNickname = "클레임값"))
    }

    /** 잘라내지 않는다 — 21자를 20자로 줄여 쓰지 않고 생성기로 물러난다. */
    @Test
    fun `두 후보가 모두 규칙을 벗어나면 자르지 않고 생성기로 물러난다`() {
        assertEquals(generatedNickname(), registeredNickname(clientNickname = "   ", providerNickname = "가".repeat(21)))
    }

    @Test
    fun `후보가 하나도 없으면 생성기로 물러난다`() {
        assertEquals(generatedNickname(), registeredNickname(clientNickname = null, providerNickname = null))
    }

    // --- 이메일 가입 ---

    @Test
    fun `이메일 가입은 보호된 식별자와 비밀번호 해시로 미인증 회원을 만들고 코드를 발급한다`() {
        val result = service.registerByEmail(emailCommand(email = "  USER@Example.COM "))

        assertFalse(result.emailVerified)
        val member = insertedMember.captured
        assertEquals(Provider.EMAIL, member.provider)
        assertEquals("protected:user@example.com", member.providerUserId.value)
        assertEquals("user@example.com", member.email.value)
        assertFalse(member.emailVerified)
        assertEquals("encoded:Passw0rd!", member.passwordHash?.value)
        // 닉네임에 이메일 로컬파트를 쓰지 않는다(R20).
        assertEquals(generatedNickname(), insertedProfile.captured.nickname.value)
        assertNull(insertedProfile.captured.profileImageUrl)

        val issued = insertedVerification.captured
        assertEquals(issued.publicId, result.verificationPublicId)
        assertEquals("hashed:123456", issued.codeHash)
        assertEquals(VerificationPurpose.REGISTRATION, issued.purpose)
        assertEquals(
            listOf(
                VerificationEmailRequested("user@example.com", "123456"),
                MemberRegistered(100L, Provider.EMAIL, emailVerified = false, verificationPublicId = issued.publicId, occurredAt = now),
            ),
            events,
        )
        verify { memberRepository.addRole(MemberRoleChange.Granted(100L, userRole), now) }
    }

    /** 이미 가입 → 이메일 중복 → 자격 순이다. 세 조건이 한꺼번에 걸려도 첫 번째가 답이다. */
    @Test
    fun `이미 가입된 이메일은 다른 사유보다 먼저 답한다`() {
        every { memberRepository.existsByProviderUserId(Provider.EMAIL, ProviderUserId("protected:user@example.com")) } returns true
        every { memberRepository.existsByEmailIdentifier(any(), any(), any()) } returns true

        val ex = assertFailsWith<MemberAlreadyRegisteredException> { service.registerByEmail(emailCommand(ageVerified = false)) }

        assertEquals("이미 가입된 이메일입니다.", ex.message)
        verify(exactly = 0) { memberRepository.existsByEmailIdentifier(any(), any(), any()) }
        verify(exactly = 0) { memberRepository.insert(any(), any(), any()) }
    }

    @Test
    fun `이메일 가입은 이메일 중복을 자격 미달보다 먼저 답한다`() {
        every { memberRepository.existsByEmailIdentifier(Provider.EMAIL, "protected:user@example.com", null) } returns true

        val ex = assertFailsWith<EmailAlreadyInUseException> { service.registerByEmail(emailCommand(ageVerified = false, agreements = emptyList())) }

        assertEquals("이미 사용 중인 이메일입니다.", ex.message)
        verify(exactly = 0) { memberRepository.insert(any(), any(), any()) }
    }

    @Test
    fun `이메일 가입도 자격 미달이면 아무것도 넣지 않는다`() {
        assertFailsWith<UnderageRegistrationException> { service.registerByEmail(emailCommand(ageVerified = false)) }
        val missingTerms = assertFailsWith<RequiredTermsNotAgreedException> { service.registerByEmail(emailCommand(agreements = emptyList())) }

        assertEquals("필수 약관에 동의해야 가입할 수 있습니다. 미동의: [SERVICE, PRIVACY]", missingTerms.message)
        verify(exactly = 0) { memberRepository.insert(any(), any(), any()) }
        assertTrue(events.isEmpty())
    }

    /** 특수문자 없음 → INVALID_INPUT이 아니라 전용 코드다(POL-022). */
    @Test
    fun `비밀번호 정책 위반은 커맨드를 만들 때 전용 코드로 나간다`() {
        val ex = assertFailsWith<PasswordPolicyViolationException> {
            RegisterEmailMemberCommand("user@example.com", "Password1", ageVerified = true, agreements = requiredAgreed)
        }

        assertEquals("PASSWORD_POLICY_VIOLATION", ex.code)
        assertEquals("비밀번호에 특수문자를 포함해야 합니다.", ex.message)
    }

    // --- 인증 코드 확인 ---

    private val publicId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private fun challenge(
        purpose: VerificationPurpose = VerificationPurpose.REGISTRATION,
        targetEmail: String? = null,
        expiresAt: Instant = now.plus(policy.ttl),
    ) = emailVerificationFixture(publicId = publicId, expiresAt = expiresAt, purpose = purpose, targetEmail = targetEmail)

    private fun givenChallenge(challenge: EmailVerification?) {
        every { memberRepository.findEmailVerification(publicId, forUpdate = true) } returns challenge
    }

    private fun givenMember(member: Member?) {
        every { memberRepository.findById(100L) } returns member
    }

    @Test
    fun `올바른 코드면 코드 소진을 남기고 회원을 인증 완료로 저장한다`() {
        givenChallenge(challenge())
        givenMember(memberFixture(emailVerified = false))

        val result = service.verifyEmail(VerifyEmailCommand(publicId, "123456"))

        assertTrue(result.verified)
        assertNull(result.failureReason)
        verifyOrder {
            memberRepository.saveEmailVerification(challenge().copy(consumed = true, verifiedAt = now))
            memberRepository.saveEmailVerified(memberFixture(emailVerified = true))
        }
        // 주소를 정정하지 않은 코드는 이메일을 건드리지 않는다.
        verify(exactly = 0) { memberRepository.saveEmail(any()) }
        verify(exactly = 0) { memberRepository.existsByEmailIdentifier(any(), any(), any()) }
        assertEquals(listOf<Any>(EmailVerified(100L, publicId, now)), events)
    }

    @Test
    fun `틀린 코드면 시도 횟수만 저장하고 회원은 그대로 둔다`() {
        givenChallenge(challenge())
        givenMember(memberFixture(emailVerified = false))

        val result = service.verifyEmail(VerifyEmailCommand(publicId, "000000"))

        assertFalse(result.verified)
        assertEquals("CODE_MISMATCH", result.failureReason)
        verify(exactly = 1) { memberRepository.saveEmailVerification(challenge().copy(attempts = 1)) }
        verify(exactly = 0) { memberRepository.saveEmailVerified(any()) }
        verify(exactly = 0) { memberRepository.saveEmail(any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `만료된 코드는 맞는 값이어도 EXPIRED로 실패한다`() {
        givenChallenge(challenge(expiresAt = now.minusSeconds(1)))
        givenMember(memberFixture(emailVerified = false))

        val result = service.verifyEmail(VerifyEmailCommand(publicId, "123456"))

        assertEquals("EXPIRED", result.failureReason)
        verify(exactly = 0) { memberRepository.saveEmailVerified(any()) }
    }

    @Test
    fun `핸들에 해당하는 코드가 없으면 없는 요청으로 답한다`() {
        givenChallenge(null)

        val ex = assertFailsWith<VerificationNotFoundException> { service.verifyEmail(VerifyEmailCommand(publicId, "123456")) }

        assertEquals("유효하지 않은 검증 요청입니다.", ex.message)
        verify(exactly = 0) { memberRepository.saveEmailVerification(any()) }
    }

    /** 이 경로는 인증이 없다. 거르지 않으면 남의 이메일 변경이 여기서 확정된다. */
    @Test
    fun `이메일 변경 핸들로는 가입 인증을 할 수 없다`() {
        givenChallenge(challenge(purpose = VerificationPurpose.EMAIL_CHANGE, targetEmail = "victim@example.com"))
        givenMember(memberFixture(emailVerified = false))

        val ex = assertFailsWith<VerificationNotFoundException> { service.verifyEmail(VerifyEmailCommand(publicId, "123456")) }

        assertEquals("유효하지 않은 검증 요청입니다.", ex.message)
        verify(exactly = 0) { memberRepository.saveEmailVerification(any()) }
        verify(exactly = 0) { memberRepository.saveEmail(any()) }
        verify(exactly = 0) { memberRepository.saveEmailVerified(any()) }
    }

    @Test
    fun `코드의 회원이 없으면 회원 없음으로 답한다`() {
        givenChallenge(challenge())
        givenMember(null)

        val ex = assertFailsWith<MemberNotFoundException> { service.verifyEmail(VerifyEmailCommand(publicId, "123456")) }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
        verify(exactly = 0) { memberRepository.saveEmailVerification(any()) }
    }

    /** R7-5. 정정한 주소는 코드를 통과할 때 확정된다. 중복을 다시 보고, 이메일 → 인증 여부 순으로 쓴다. */
    @Test
    fun `정정한 주소의 코드를 통과하면 중복을 다시 보고 이메일을 확정한 뒤 인증 완료로 저장한다`() {
        givenChallenge(challenge(targetEmail = "right@example.com"))
        givenMember(memberFixture(provider = Provider.EMAIL, providerUserId = "protected:wrong@example.com", email = "wrong@example.com", emailVerified = false))

        val result = service.verifyEmail(VerifyEmailCommand(publicId, "123456"))

        val confirmed = memberFixture(
            provider = Provider.EMAIL,
            providerUserId = "protected:right@example.com",
            email = "right@example.com",
            emailVerified = true,
        )
        assertTrue(result.verified)
        verifyOrder {
            memberRepository.saveEmailVerification(any())
            memberRepository.existsByEmailIdentifier(Provider.EMAIL, "protected:right@example.com", 100L)
            memberRepository.saveEmail(confirmed)
            memberRepository.saveEmailVerified(confirmed)
        }
        assertEquals(listOf<Any>(EmailVerified(100L, publicId, now)), events)
    }

    /** SNS 회원의 식별자는 provider가 준 값이라 주소가 바뀌어도 그대로다. */
    @Test
    fun `SNS 회원의 주소 확정은 로그인 식별자를 바꾸지 않는다`() {
        givenChallenge(challenge(targetEmail = "right@example.com"))
        givenMember(memberFixture(provider = Provider.KAKAO, providerUserId = "kakao-9911", email = "wrong@example.com", emailVerified = false))
        val saved = slot<Member>()
        every { memberRepository.saveEmail(capture(saved)) } just runs

        service.verifyEmail(VerifyEmailCommand(publicId, "123456"))

        assertEquals("kakao-9911", saved.captured.providerUserId.value)
        assertEquals("right@example.com", saved.captured.email.value)
        verify { memberRepository.existsByEmailIdentifier(Provider.KAKAO, "protected:right@example.com", 100L) }
    }

    /** 발급과 확정 사이에 다른 회원이 그 주소를 가져간 경우. */
    @Test
    fun `확정 시점에 정정한 주소가 선점됐으면 이메일도 인증 여부도 쓰지 않는다`() {
        givenChallenge(challenge(targetEmail = "right@example.com"))
        givenMember(memberFixture(emailVerified = false))
        every { memberRepository.existsByEmailIdentifier(Provider.EMAIL, "protected:right@example.com", 100L) } returns true

        val ex = assertFailsWith<EmailAlreadyInUseException> { service.verifyEmail(VerifyEmailCommand(publicId, "123456")) }

        assertEquals("이미 사용 중인 이메일입니다.", ex.message)
        verify(exactly = 0) { memberRepository.saveEmail(any()) }
        verify(exactly = 0) { memberRepository.saveEmailVerified(any()) }
        assertTrue(events.isEmpty())
    }

    // --- 인증 코드 재발송 ---

    private fun givenResendChallenge(challenge: EmailVerification?) {
        every { memberRepository.findEmailVerification(publicId, forUpdate = false) } returns challenge
    }

    @Test
    fun `재발송은 쓰던 코드를 폐기하고 새 코드를 회원의 주소로 보낸다`() {
        val previous = challenge().copy(createdAt = now.minusSeconds(61))
        val alreadyConsumed = challenge().copy(id = 2L, consumed = true, createdAt = now.minusSeconds(600))
        givenResendChallenge(previous)
        givenMember(memberFixture(email = "user@example.com", emailVerified = false))
        every { memberRepository.findEmailVerifications(100L, VerificationPurpose.REGISTRATION) } returns listOf(alreadyConsumed, previous)

        val result = service.resendVerification(ResendVerificationCommand(publicId))

        val issued = insertedVerification.captured
        assertEquals(issued.publicId, result.verificationPublicId)
        assertEquals(now.plus(policy.ttl), result.expiresAt)
        assertEquals("hashed:123456", issued.codeHash)
        assertEquals(VerificationPurpose.REGISTRATION, issued.purpose)
        assertNull(issued.targetEmail)
        // 살아 있던 코드만 폐기로 저장한다. 이미 소진된 것은 다시 쓰지 않는다.
        verify(exactly = 1) { memberRepository.saveEmailVerification(any()) }
        verifyOrder {
            memberRepository.saveEmailVerification(previous.copy(consumed = true))
            memberRepository.insertEmailVerification(issued)
        }
        assertEquals(listOf<Any>(VerificationEmailRequested("user@example.com", "123456")), events)
    }

    /** 회원의 이메일로 되돌리면 정정이 조용히 취소된다. */
    @Test
    fun `주소를 정정한 뒤의 재발송은 정정한 주소로 보내고 그 주소를 이어받는다`() {
        givenResendChallenge(challenge(targetEmail = "right@example.com"))
        givenMember(memberFixture(email = "wrong@example.com", emailVerified = false))

        service.resendVerification(ResendVerificationCommand(publicId))

        assertEquals(Email("right@example.com"), insertedVerification.captured.targetEmail)
        assertEquals(listOf<Any>(VerificationEmailRequested("right@example.com", "123456")), events)
    }

    @Test
    fun `재발송은 쿨다운 안이면 VERIFICATION_RATE_LIMITED로 거부한다`() {
        val previous = challenge().copy(createdAt = now.minusSeconds(30))
        givenResendChallenge(previous)
        givenMember(memberFixture(emailVerified = false))
        every { memberRepository.findEmailVerifications(100L, VerificationPurpose.REGISTRATION) } returns listOf(previous)

        val ex = assertFailsWith<VerificationRateLimitedException> { service.resendVerification(ResendVerificationCommand(publicId)) }

        assertEquals("VERIFICATION_RATE_LIMITED", ex.code)
        assertEquals("잠시 후 다시 시도해 주세요.", ex.message)
        verify(exactly = 0) { memberRepository.saveEmailVerification(any()) }
        verify(exactly = 0) { memberRepository.insertEmailVerification(any()) }
        assertTrue(events.isEmpty())
    }

    /** 거르지 않으면 변경 대상 주소로 가입 인증 코드를 보내게 된다. */
    @Test
    fun `이메일 변경 핸들이나 없는 핸들로는 재발송할 수 없다`() {
        givenResendChallenge(challenge(purpose = VerificationPurpose.EMAIL_CHANGE, targetEmail = "victim@example.com"))
        val wrongPurpose = assertFailsWith<VerificationNotFoundException> { service.resendVerification(ResendVerificationCommand(publicId)) }
        givenResendChallenge(null)
        val missing = assertFailsWith<VerificationNotFoundException> { service.resendVerification(ResendVerificationCommand(publicId)) }

        assertEquals("유효하지 않은 검증 요청입니다.", wrongPurpose.message)
        assertEquals("유효하지 않은 검증 요청입니다.", missing.message)
        verify(exactly = 0) { memberRepository.insertEmailVerification(any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `재발송 대상 회원이 없으면 검증 요청 없음으로 답한다`() {
        givenResendChallenge(challenge())
        givenMember(null)

        val ex = assertFailsWith<VerificationNotFoundException> { service.resendVerification(ResendVerificationCommand(publicId)) }

        assertEquals("회원 정보를 찾을 수 없습니다.", ex.message)
        verify(exactly = 0) { memberRepository.insertEmailVerification(any()) }
    }

    // --- 인증 주소 정정 (SPEC-0024 R7) ---

    private val retarget = RetargetEmailVerificationCommand(100L, " Right@Example.com ")

    /** R7-1. 회원의 이메일은 아직 바꾸지 않는다 — 새 주소는 코드가 들고 있다가 통과하면 확정된다. */
    @Test
    fun `주소를 정정하면 쓰던 코드를 폐기하고 새 주소를 담은 코드를 그 주소로 보낸다`() {
        val previous = challenge().copy(createdAt = now.minusSeconds(60))
        givenMember(memberFixture(provider = Provider.KAKAO, email = "wrong@example.com", emailVerified = false))
        every { memberRepository.findEmailVerifications(100L, VerificationPurpose.REGISTRATION) } returns listOf(previous)

        val result = service.retargetEmailVerification(retarget)

        val issued = insertedVerification.captured
        assertEquals(issued.publicId, result.verificationPublicId)
        assertEquals(now.plus(policy.ttl), result.expiresAt)
        assertEquals(Email("right@example.com"), issued.targetEmail)
        assertEquals(VerificationPurpose.REGISTRATION, issued.purpose)
        assertEquals("hashed:123456", issued.codeHash)
        verify { memberRepository.saveEmailVerification(previous.copy(consumed = true)) }
        verify(exactly = 0) { memberRepository.saveEmail(any()) }
        verify(exactly = 0) { memberRepository.findEmailVerifications(any(), VerificationPurpose.EMAIL_CHANGE) }
        verify { memberRepository.existsByEmailIdentifier(Provider.KAKAO, "protected:right@example.com", 100L) }
        assertEquals(
            listOf(
                VerificationEmailRequested("right@example.com", "123456"),
                EmailVerificationRetargeted(100L, Provider.KAKAO),
            ),
            events,
        )
    }

    /** 인증 완료 → 중복 → 쿨다운. 세 조건이 모두 걸린 회원에게는 ALREADY_VERIFIED가 나간다. */
    @Test
    fun `주소 정정은 인증 완료를 중복과 쿨다운보다 먼저 답한다`() {
        givenMember(memberFixture(emailVerified = true))
        every { memberRepository.existsByEmailIdentifier(any(), any(), any()) } returns true
        every { memberRepository.findEmailVerifications(100L, VerificationPurpose.REGISTRATION) } returns listOf(challenge().copy(createdAt = now))

        val ex = assertFailsWith<AlreadyVerifiedException> { service.retargetEmailVerification(retarget) }

        assertEquals("ALREADY_VERIFIED", ex.code)
        assertEquals("이미 이메일 인증을 마쳤습니다.", ex.message)
        verify(exactly = 0) { memberRepository.existsByEmailIdentifier(any(), any(), any()) }
        verify(exactly = 0) { memberRepository.insertEmailVerification(any()) }
    }

    @Test
    fun `주소 정정은 중복을 쿨다운보다 먼저 답한다`() {
        givenMember(memberFixture(emailVerified = false))
        every { memberRepository.existsByEmailIdentifier(Provider.EMAIL, "protected:right@example.com", 100L) } returns true
        every { memberRepository.findEmailVerifications(100L, VerificationPurpose.REGISTRATION) } returns listOf(challenge().copy(createdAt = now))

        val ex = assertFailsWith<EmailAlreadyInUseException> { service.retargetEmailVerification(retarget) }

        assertEquals("이미 사용 중인 이메일입니다.", ex.message)
        verify(exactly = 0) { codeGenerator.generate() }
        verify(exactly = 0) { memberRepository.insertEmailVerification(any()) }
    }

    /** R7-3. 창은 재발송과 함께 세지만 코드는 RESEND_COOLDOWN_ACTIVE다. */
    @Test
    fun `주소 정정은 쿨다운 안이면 RESEND_COOLDOWN_ACTIVE로 거부한다`() {
        givenMember(memberFixture(emailVerified = false))
        every { memberRepository.findEmailVerifications(100L, VerificationPurpose.REGISTRATION) } returns listOf(challenge().copy(createdAt = now.minusSeconds(59)))

        val ex = assertFailsWith<ResendCooldownActiveException> { service.retargetEmailVerification(retarget) }

        assertEquals("RESEND_COOLDOWN_ACTIVE", ex.code)
        assertEquals("잠시 후 다시 시도해 주세요.", ex.message)
        verify(exactly = 0) { memberRepository.saveEmailVerification(any()) }
        verify(exactly = 0) { memberRepository.insertEmailVerification(any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `주소 정정 대상 회원이 없으면 회원 없음으로 답한다`() {
        givenMember(null)

        val ex = assertFailsWith<MemberNotFoundException> { service.retargetEmailVerification(retarget) }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
    }
}
