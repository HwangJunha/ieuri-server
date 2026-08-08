package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.auth.application.command.ChangeEmailCommand
import org.around.ieuri.auth.application.command.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.command.ResendEmailChangeCommand
import org.around.ieuri.auth.application.event.VerificationEmailRequested
import org.around.ieuri.auth.application.exception.EmailAlreadyInUseException
import org.around.ieuri.auth.application.exception.InvalidEmailFormatException
import org.around.ieuri.auth.application.exception.ResendCooldownActiveException
import org.around.ieuri.auth.application.exception.SameAsCurrentEmailException
import org.around.ieuri.auth.application.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.policy.VerificationPolicy
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.EmailEncryptorPort
import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.port.out.security.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.aggregate.Member
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이메일 변경(SPEC-0011 R7·R8).
 *
 * 고정하는 계약: **확인을 통과하기 전까지 이메일은 바뀌지 않는다**, 코드 실패는 오류가 아니라
 * `failureReason`이다, 그리고 **`EMAIL` 회원은 로그인 식별자가 함께 갱신된다**(R8) —
 * 그러지 않으면 이메일을 바꾼 회원이 로그인하지 못한다.
 */
class EmailChangeServiceTest {

    private val memberId = 14L
    private val now = Instant.parse("2026-08-09T00:00:00Z")
    private val currentEmail = "old@example.com"
    private val newEmail = "new@example.com"
    private val publicId = UUID.randomUUID()

    /**
     * **목이 아니라 손으로 쓴 페이크다.** `Email`이 value class라 mockk가 그 반환을 스텁하지
     * 못한다(언박싱 규약이 어긋나 ClassCastException). 이 포트만 페이크로 두고 나머지는 목이다.
     */
    private val memberRepository = FakeMemberRepository()
    private val verificationRepository = mockk<EmailVerificationRepositoryPort>(relaxed = true)
    private val auditLog = mockk<AuditLogPort>(relaxed = true)
    private val eventPublisher = mockk<DomainEventPublisherPort>(relaxed = true)
    private val policy = VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60))

    private val codeGenerator = mockk<VerificationCodeGeneratorPort> {
        every { generate() } returns "123456"
        every { hash(any()) } answers { "hashed:${firstArg<String>()}" }
    }

    /** 암호화 스텁: 가역이고 평문과 구분되게 접두사를 붙인다(실제 AES는 어댑터 관심사). */
    private val emailEncryptor = object : EmailEncryptorPort {
        override fun encrypt(plaintext: String) = "enc:$plaintext"
        override fun decrypt(ciphertext: String) = ciphertext.removePrefix("enc:")
    }

    /** 보호 변환 스텁: 결정론적이어야 등호 조회가 성립한다. */
    private val identifierProtector = object : LoginIdentifierProtectorPort {
        override fun protect(normalizedIdentifier: String) = "protected:$normalizedIdentifier"
    }

    private val service = EmailChangeService(
        memberRepository,
        verificationRepository,
        codeGenerator,
        emailEncryptor,
        identifierProtector,
        policy,
        auditLog,
        eventPublisher,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun givenCurrentEmail(email: String = currentEmail) {
        memberRepository.currentEmail = Email(email)
    }

    private fun givenNotTaken() {
        memberRepository.identifierTaken = false
    }

    private fun givenMember(provider: Provider) {
        memberRepository.member = Member.reconstitute(
            id = memberId,
            provider = provider,
            providerUserId = ProviderUserId("whatever"),
            email = Email(currentEmail),
            emailVerified = true,
            status = MemberStatus.ACTIVE,
            ageVerified = true,
            suspendedAt = null,
            suspendedUntil = null,
            suspensionReason = null,
            nickname = Nickname("테스트계정"),
            profileImageUrl = null,
            createdAt = now,
        )
    }

    private fun challenge(
        codeHash: String = "hashed:123456",
        target: String = newEmail,
        purpose: VerificationPurpose = VerificationPurpose.EMAIL_CHANGE,
        owner: Long = memberId,
        issuedAt: Instant = now,
    ) = EmailVerification.issue(
        publicId = publicId,
        memberId = owner,
        codeHash = codeHash,
        ttl = policy.ttl,
        now = issuedAt,
        maxAttempts = policy.maxAttempts,
        purpose = purpose,
        targetEmailEncrypted = if (purpose == VerificationPurpose.EMAIL_CHANGE) "enc:$target" else null,
    )

    // --- R7 변경 요청 ---

    @Test
    fun `쓰이지 않는 주소면 핸들을 발급하고 새 주소로 코드를 보낸다`() {
        givenCurrentEmail()
        givenNotTaken()
        val event = slot<Any>()
        every { eventPublisher.publish(capture(event)) } returns Unit

        val result = service.changeEmail(ChangeEmailCommand(memberId, newEmail))

        assertEquals(now.plus(policy.ttl), result.expiresAt)
        // **새 주소로** 보낸다 — 기존 주소로 보내면 변경을 확인할 수 없다.
        val requested = event.captured as VerificationEmailRequested
        assertEquals(newEmail, requested.toEmail)
        assertEquals("123456", requested.code)
    }

    @Test
    fun `요청 단계에서는 회원의 이메일을 바꾸지 않는다`() {
        givenCurrentEmail()
        givenNotTaken()

        service.changeEmail(ChangeEmailCommand(memberId, newEmail))

        assertEquals(0, memberRepository.updateEmailCallCount)
    }

    @Test
    fun `대상 주소를 암호문으로 저장한다`() {
        givenCurrentEmail()
        givenNotTaken()
        val saved = slot<EmailVerification>()
        every { verificationRepository.save(capture(saved)) } returns Unit

        service.changeEmail(ChangeEmailCommand(memberId, newEmail))

        // 검증 통과 전이라도 개인정보다(POL-012) — 평문이 그대로 들어가면 안 된다.
        assertEquals("enc:$newEmail", saved.captured.targetEmailEncrypted)
        assertEquals(VerificationPurpose.EMAIL_CHANGE, saved.captured.purpose)
    }

    @Test
    fun `다른 회원이 쓰는 주소면 거절한다`() {
        givenCurrentEmail()
        memberRepository.identifierTaken = true

        assertFailsWith<EmailAlreadyInUseException> {
            service.changeEmail(ChangeEmailCommand(memberId, newEmail))
        }
        verify(exactly = 0) { verificationRepository.save(any()) }
    }

    @Test
    fun `중복 실패 메시지에 상대 정보를 담지 않는다`() {
        givenCurrentEmail()
        memberRepository.identifierTaken = true

        val e = assertFailsWith<EmailAlreadyInUseException> {
            service.changeEmail(ChangeEmailCommand(memberId, newEmail))
        }

        assertEquals("EMAIL_ALREADY_IN_USE", e.code)
        assertFalse(e.message!!.contains(newEmail))
    }

    @Test
    fun `현재 자신의 이메일과 같으면 거절한다`() {
        givenCurrentEmail()

        assertFailsWith<SameAsCurrentEmailException> {
            service.changeEmail(ChangeEmailCommand(memberId, currentEmail))
        }
        verify(exactly = 0) { verificationRepository.save(any()) }
    }

    @Test
    fun `형식이 올바르지 않으면 거절한다`() {
        givenCurrentEmail()

        assertFailsWith<InvalidEmailFormatException> {
            service.changeEmail(ChangeEmailCommand(memberId, "not-an-email"))
        }
    }

    @Test
    fun `요청은 이메일 변경 챌린지만 폐기한다`() {
        givenCurrentEmail()
        givenNotTaken()

        service.changeEmail(ChangeEmailCommand(memberId, newEmail))

        // 가입 검증이 진행 중이면 그건 건드리지 않는다.
        verify {
            verificationRepository.consumeActiveByMemberIdAndPurpose(
                memberId, VerificationPurpose.EMAIL_CHANGE, now,
            )
        }
        verify(exactly = 0) { verificationRepository.consumeActiveByMemberId(any(), any()) }
    }

    // --- R7 확인 ---

    @Test
    fun `올바른 코드면 이메일이 교체된다`() {
        every { verificationRepository.findByPublicId(publicId) } returns challenge()
        givenNotTaken()
        givenMember(Provider.APPLE)

        val result = service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "123456"))

        assertTrue(result.changed)
        assertEquals(newEmail, result.email)
        assertNull(result.failureReason)
        assertEquals(Email(newEmail) to null, memberRepository.updatedEmail)
    }

    @Test
    fun `틀린 코드면 실패 사유를 돌려주고 이메일을 바꾸지 않는다`() {
        every { verificationRepository.findByPublicId(publicId) } returns challenge()
        givenNotTaken()

        val result = service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "999999"))

        assertFalse(result.changed)
        assertEquals("CODE_MISMATCH", result.failureReason)
        assertNull(result.email)
        assertEquals(0, memberRepository.updateEmailCallCount)
    }

    @Test
    fun `만료된 핸들이면 EXPIRED다`() {
        every { verificationRepository.findByPublicId(publicId) } returns
            challenge(issuedAt = now.minus(Duration.ofMinutes(11)))
        givenNotTaken()

        val result = service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "123456"))

        assertEquals("EXPIRED", result.failureReason)
        assertEquals(0, memberRepository.updateEmailCallCount)
    }

    @Test
    fun `시도를 모두 소진하면 MAX_ATTEMPTS_EXCEEDED다`() {
        val exhausted = challenge()
        repeat(policy.maxAttempts) { exhausted.verify("hashed:wrong", now) }
        every { verificationRepository.findByPublicId(publicId) } returns exhausted
        givenNotTaken()

        val result = service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "123456"))

        assertEquals("CONSUMED", result.failureReason)
    }

    @Test
    fun `이미 성공한 핸들을 다시 쓰면 CONSUMED이고 이메일이 다시 바뀌지 않는다`() {
        val consumed = challenge()
        consumed.verify("hashed:123456", now)
        every { verificationRepository.findByPublicId(publicId) } returns consumed
        givenNotTaken()

        val result = service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "123456"))

        assertEquals("CONSUMED", result.failureReason)
        assertEquals(0, memberRepository.updateEmailCallCount)
    }

    @Test
    fun `실패해도 시도 횟수 반영을 위해 갱신한다`() {
        every { verificationRepository.findByPublicId(publicId) } returns challenge()
        givenNotTaken()

        service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "999999"))

        verify { verificationRepository.update(any()) }
    }

    @Test
    fun `남의 핸들은 없는 것과 같이 거절한다`() {
        every { verificationRepository.findByPublicId(publicId) } returns challenge(owner = 99L)

        assertFailsWith<VerificationNotFoundException> {
            service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "123456"))
        }
    }

    @Test
    fun `가입 검증 핸들로는 이메일을 바꿀 수 없다`() {
        every { verificationRepository.findByPublicId(publicId) } returns
            challenge(purpose = VerificationPurpose.REGISTRATION)

        assertFailsWith<VerificationNotFoundException> {
            service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "123456"))
        }
    }

    @Test
    fun `확인 시점에 그 주소가 선점됐으면 거절한다`() {
        // 요청과 확인 사이에 다른 회원이 그 주소로 가입할 수 있다.
        every { verificationRepository.findByPublicId(publicId) } returns challenge()
        memberRepository.identifierTaken = true

        assertFailsWith<EmailAlreadyInUseException> {
            service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "123456"))
        }
        assertEquals(0, memberRepository.updateEmailCallCount)
    }

    // --- R8 가입 유형 무관 ---

    @Test
    fun `EMAIL 회원은 로그인 식별자도 함께 갱신된다`() {
        // 이것이 없으면 이메일을 바꾼 회원이 다음 로그인에 실패한다.
        every { verificationRepository.findByPublicId(publicId) } returns challenge()
        givenNotTaken()
        givenMember(Provider.EMAIL)

        service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "123456"))

        assertEquals(Email(newEmail) to "protected:$newEmail", memberRepository.updatedEmail)
    }

    @Test
    fun `SNS 회원은 로그인 식별자를 건드리지 않는다`() {
        // provider가 준 불투명 식별자라 이메일과 무관하다 — 바꾸면 SNS 로그인이 깨진다.
        every { verificationRepository.findByPublicId(publicId) } returns challenge()
        givenNotTaken()
        givenMember(Provider.KAKAO)

        service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "123456"))

        assertEquals(Email(newEmail) to null, memberRepository.updatedEmail)
    }

    @Test
    fun `감사 로그에 주소를 담지 않는다`() {
        every { verificationRepository.findByPublicId(publicId) } returns challenge()
        givenNotTaken()
        givenMember(Provider.EMAIL)
        val payload = slot<Map<String, Any?>>()
        every { auditLog.record(memberId, "EMAIL_CHANGED", capture(payload)) } returns Unit

        service.confirmEmailChange(ConfirmEmailChangeCommand(memberId, publicId, "123456"))

        // POL-013 — 무엇이 바뀌었는지는 회원 데이터가 갖는다.
        assertFalse(payload.captured.values.any { it.toString().contains("@") })
        assertEquals(true, payload.captured["loginIdentifierUpdated"])
    }

    // --- R7 재발송 ---

    @Test
    fun `쿨다운이 지났으면 같은 주소로 다시 보낸다`() {
        every { verificationRepository.findByPublicId(publicId) } returns challenge()
        every {
            verificationRepository.findLatestCreatedAtByMemberIdAndPurpose(memberId, VerificationPurpose.EMAIL_CHANGE)
        } returns now.minus(Duration.ofSeconds(61))
        val event = slot<Any>()
        every { eventPublisher.publish(capture(event)) } returns Unit

        service.resendEmailChange(ResendEmailChangeCommand(memberId, publicId))

        // 대상 주소는 이전 챌린지에서 이어받는다 — 주소를 바꾸려면 changeEmail을 다시 부른다.
        assertEquals(newEmail, (event.captured as VerificationEmailRequested).toEmail)
    }

    @Test
    fun `쿨다운 이내면 거절한다`() {
        every { verificationRepository.findByPublicId(publicId) } returns challenge()
        every {
            verificationRepository.findLatestCreatedAtByMemberIdAndPurpose(memberId, VerificationPurpose.EMAIL_CHANGE)
        } returns now.minus(Duration.ofSeconds(30))

        assertFailsWith<ResendCooldownActiveException> {
            service.resendEmailChange(ResendEmailChangeCommand(memberId, publicId))
        }
        verify(exactly = 0) { verificationRepository.save(any()) }
    }

    @Test
    fun `쿨다운은 이메일 변경 용도로만 센다`() {
        // 가입 검증과 창을 공유하면 한쪽을 보낸 직후 다른 쪽이 막힌다.
        every { verificationRepository.findByPublicId(publicId) } returns challenge()
        every {
            verificationRepository.findLatestCreatedAtByMemberIdAndPurpose(memberId, VerificationPurpose.EMAIL_CHANGE)
        } returns null

        service.resendEmailChange(ResendEmailChangeCommand(memberId, publicId))

        verify(exactly = 0) { verificationRepository.findLatestCreatedAtByMemberId(any()) }
    }

    @Test
    fun `재발송도 남의 핸들은 거절한다`() {
        every { verificationRepository.findByPublicId(publicId) } returns challenge(owner = 99L)

        assertFailsWith<VerificationNotFoundException> {
            service.resendEmailChange(ResendEmailChangeCommand(memberId, publicId))
        }
    }
}

/**
 * `MemberRepositoryPort`의 최소 페이크. **이 슬라이스가 쓰는 네 메서드만 실동작하고**
 * 나머지는 쓰이지 않으므로 호출되면 실패시킨다 — 조용히 no-op이면 검증이 헐거워진다.
 */
private class FakeMemberRepository : MemberRepositoryPort {
    var currentEmail: Email? = Email("old@example.com")
    var identifierTaken: Boolean = false
    var member: Member? = null

    /** 마지막 updateEmail 호출 인자. null이면 호출되지 않았다는 뜻이다. */
    var updatedEmail: Pair<Email, String?>? = null
    var updateEmailCallCount: Int = 0

    override fun findEmailByMemberId(memberId: Long): Email? = currentEmail

    override fun existsByProviderAndProviderUserId(provider: Provider, providerUserId: ProviderUserId): Boolean =
        identifierTaken

    override fun load(memberId: Long): Member? = member

    override fun updateEmail(memberId: Long, newEmail: Email, protectedIdentifier: String?) {
        updateEmailCallCount++
        updatedEmail = newEmail to protectedIdentifier
    }

    override fun existsById(memberId: Long): Boolean = unused()
    override fun save(member: Member): Long = unused()
    override fun markEmailVerified(memberId: Long, verifiedAt: java.time.Instant): Unit = unused()
    override fun updatePassword(memberId: Long, newPasswordHash: String): Unit = unused()
    override fun findAuthentication(provider: Provider, providerUserId: ProviderUserId) = unused()
    override fun findEmailAuthentication(protectedIdentifier: String) = unused()
    override fun updateProviderRefreshToken(memberId: Long, refreshToken: String): Unit = unused()
    override fun findProviderRefreshToken(memberId: Long): String? = unused()
    override fun updateStatus(member: Member): Unit = unused()
    override fun deleteMember(memberId: Long): Unit = unused()

    private fun unused(): Nothing = error("이 테스트가 쓰지 않는 포트 메서드가 호출됐다.")
}
