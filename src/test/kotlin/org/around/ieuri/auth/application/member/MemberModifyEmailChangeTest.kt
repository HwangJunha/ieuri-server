package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.dto.ChangeEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.exception.EmailAlreadyInUseException
import org.around.ieuri.auth.application.member.provided.exception.InvalidEmailFormatException
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.EmailVerification
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.NotEmailMemberException
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ResendCooldownActiveException
import org.around.ieuri.auth.domain.member.SameAsCurrentEmailException
import org.around.ieuri.auth.domain.member.VerificationNotFoundException
import org.around.ieuri.auth.domain.member.VerificationPurpose
import org.around.ieuri.auth.domain.member.event.EmailChanged
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이메일 변경(SPEC-0011 R7·R8).
 * - 확인을 통과하기 전까지 이메일은 바뀌지 않고, 코드 실패는 오류가 아니라 `failureReason`이다.
 * - 자체(EMAIL) 회원은 로그인 식별자가 함께 바뀐다(R8).
 */
class MemberModifyEmailChangeTest {

    private val memberId = 14L
    private val now = Instant.parse("2026-08-09T00:00:00Z")
    private val currentEmail = "old@example.com"
    private val newEmail = "new@example.com"
    private val publicId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val policy = testVerificationPolicy

    private val memberRepository = mockk<MemberRepository>(relaxUnitFun = true)
    private val codeGenerator = fixedCodeGenerator()
    private val eventPublisher = mockk<MemberEventPublisherPort>()
    private val events = mutableListOf<Any>()

    private val service = memberModifyService(
        memberRepository,
        clock = Clock.fixed(now, ZoneOffset.UTC),
        eventPublisher = eventPublisher,
        codeGenerator = codeGenerator,
    )

    private val issued = slot<EmailVerification>()

    init {
        every { eventPublisher.publish(capture(events)) } just runs
        every { memberRepository.existsByEmailIdentifier(any(), any(), any()) } returns false
        every { memberRepository.findEmailVerifications(any(), any()) } returns emptyList()
        every { memberRepository.insertEmailVerification(capture(issued)) } just runs
    }

    private fun member(provider: Provider = Provider.EMAIL, providerUserId: String = "protected:$currentEmail") = memberFixture(id = memberId, provider = provider, providerUserId = providerUserId, email = currentEmail)

    private fun givenMember(member: Member?) {
        every { memberRepository.findById(memberId) } returns member
    }

    private fun challenge(
        owner: Long = memberId,
        purpose: VerificationPurpose = VerificationPurpose.EMAIL_CHANGE,
        targetEmail: String? = newEmail,
        createdAt: Instant? = now.minusSeconds(120),
        expiresAt: Instant = now.plusSeconds(480),
        consumed: Boolean = false,
    ) = emailVerificationFixture(
        publicId = publicId,
        memberId = owner,
        expiresAt = expiresAt,
        purpose = purpose,
        targetEmail = targetEmail,
        createdAt = createdAt,
        consumed = consumed,
    )

    // --- 변경 요청 ---

    /** 새 주소로 보낸다 — 기존 주소로 보내면 변경을 확인할 수 없다. 회원의 이메일은 아직 그대로다. */
    @Test
    fun `변경 요청은 진행 중이던 변경 코드를 폐기하고 새 주소로 코드를 보낸다`() {
        val previous = challenge(targetEmail = "earlier@example.com")
        givenMember(member())
        every { memberRepository.findEmailVerifications(memberId, VerificationPurpose.EMAIL_CHANGE) } returns listOf(previous)

        val result = service.changeEmail(ChangeEmailCommand(" New@Example.com "), memberId, ipAddress = "203.0.113.7")

        assertEquals(issued.captured.publicId, result.verificationPublicId)
        assertEquals(now.plus(policy.ttl), result.expiresAt)
        assertEquals(memberId, issued.captured.memberId)
        assertEquals(VerificationPurpose.EMAIL_CHANGE, issued.captured.purpose)
        assertEquals(Email(newEmail), issued.captured.targetEmail)
        assertEquals("hashed:123456", issued.captured.codeHash)
        verifyOrder {
            memberRepository.consumeEmailVerification(previous.copy(consumed = true))
            memberRepository.insertEmailVerification(any())
        }
        // 폐기는 consumed 한 열만 쓰는 연산으로 한다 — 시도 횟수·통과 시각을 함께 쓰는 저장은 부르지 않는다.
        verify(exactly = 0) { memberRepository.saveEmailVerification(any()) }
        // 가입 인증이 진행 중이면 그건 건드리지 않는다.
        verify(exactly = 0) { memberRepository.findEmailVerifications(any(), VerificationPurpose.REGISTRATION) }
        verify(exactly = 0) { memberRepository.saveEmail(any()) }
        verify { memberRepository.existsByEmailIdentifier(Provider.EMAIL, "protected:$newEmail", memberId) }
        assertEquals(
            listOf(
                MemberAccessed(memberId, AccessType.EMAIL_CHANGE, "203.0.113.7"),
                VerificationEmailRequested(newEmail, "123456"),
            ),
            events,
        )
    }

    /** 접속지는 Command가 아니라 인자로 받는다 — 알 수 없으면 null 그대로 남긴다. */
    @Test
    fun `접속지를 알 수 없는 변경 요청은 접속기록에 null을 남긴다`() {
        givenMember(member())

        service.changeEmail(ChangeEmailCommand(newEmail), memberId, ipAddress = null)

        assertEquals(MemberAccessed(memberId, AccessType.EMAIL_CHANGE, null), events.first())
    }

    /** 형식 → 회원 → 변경 가능 → 동일 주소 → 중복 순. 형식 오류는 회원을 읽기도 전에 나간다. */
    @Test
    fun `형식이 틀린 주소는 회원을 읽기 전에 거부한다`() {
        givenMember(null)

        val ex = assertFailsWith<InvalidEmailFormatException> { service.changeEmail(ChangeEmailCommand("not-an-email"), memberId, ipAddress = null) }

        assertEquals("이메일 형식이 올바르지 않습니다.", ex.message)
        verify(exactly = 0) { memberRepository.findById(any()) }
    }

    @Test
    fun `변경 요청 회원이 없으면 회원 없음으로 답한다`() {
        givenMember(null)

        val ex = assertFailsWith<MemberNotFoundException> { service.changeEmail(ChangeEmailCommand(newEmail), memberId, ipAddress = null) }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `SNS 회원의 변경 요청은 동일 주소와 중복보다 먼저 NOT_EMAIL_MEMBER로 거부한다`() {
        givenMember(member(provider = Provider.KAKAO, providerUserId = "kakao-9911"))
        every { memberRepository.existsByEmailIdentifier(any(), any(), any()) } returns true

        val ex = assertFailsWith<NotEmailMemberException> { service.changeEmail(ChangeEmailCommand(currentEmail), memberId, ipAddress = null) }

        assertEquals("SNS 계정은 이메일을 변경할 수 없습니다.", ex.message)
        verify(exactly = 0) { memberRepository.existsByEmailIdentifier(any(), any(), any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `현재 주소와 같으면 중복보다 먼저 SAME_AS_CURRENT_EMAIL로 거부한다`() {
        givenMember(member())
        every { memberRepository.existsByEmailIdentifier(any(), any(), any()) } returns true

        val ex = assertFailsWith<SameAsCurrentEmailException> { service.changeEmail(ChangeEmailCommand("OLD@example.com"), memberId, ipAddress = null) }

        assertEquals("현재 사용 중인 이메일과 같습니다.", ex.message)
        verify(exactly = 0) { memberRepository.existsByEmailIdentifier(any(), any(), any()) }
        verify(exactly = 0) { memberRepository.insertEmailVerification(any()) }
    }

    /** 메시지에 상대 회원의 정보를 담지 않는다(POL-013). */
    @Test
    fun `다른 회원이 쓰는 주소면 코드를 만들지 않고 거부한다`() {
        givenMember(member())
        every { memberRepository.existsByEmailIdentifier(Provider.EMAIL, "protected:$newEmail", memberId) } returns true

        val ex = assertFailsWith<EmailAlreadyInUseException> { service.changeEmail(ChangeEmailCommand(newEmail), memberId, ipAddress = null) }

        assertEquals("이미 사용 중인 이메일입니다.", ex.message)
        verify(exactly = 0) { codeGenerator.generate() }
        verify(exactly = 0) { memberRepository.insertEmailVerification(any()) }
        assertTrue(events.isEmpty())
    }

    // --- 확인 ---

    private val confirm = ConfirmEmailChangeCommand(publicId, "123456")

    private fun givenChallenge(challenge: EmailVerification?, forUpdate: Boolean = true) {
        every { memberRepository.findEmailVerification(publicId, forUpdate) } returns challenge
    }

    /** 식별자가 갈리면 이메일을 바꾼 회원이 다음 로그인에 실패한다. */
    @Test
    fun `올바른 코드면 자체 회원의 이메일과 로그인 식별자를 함께 바꾼다`() {
        givenChallenge(challenge())
        givenMember(member())

        val result = service.confirmEmailChange(confirm, memberId)

        assertTrue(result.changed)
        assertEquals(newEmail, result.email)
        assertNull(result.failureReason)
        verifyOrder {
            memberRepository.saveEmailVerification(challenge().copy(consumed = true, verifiedAt = now))
            memberRepository.existsByEmailIdentifier(Provider.EMAIL, "protected:$newEmail", memberId)
            memberRepository.saveEmail(memberFixture(id = memberId, provider = Provider.EMAIL, providerUserId = "protected:$newEmail", email = newEmail))
        }
        assertEquals(listOf<Any>(EmailChanged(memberId, Provider.EMAIL, loginIdentifierUpdated = true)), events)
    }

    /** provider가 준 불투명 식별자는 이메일과 무관하다 — 바꾸면 SNS 로그인이 깨진다. */
    @Test
    fun `SNS 회원의 변경 확인은 로그인 식별자를 그대로 두고 그렇게 알린다`() {
        givenChallenge(challenge())
        givenMember(member(provider = Provider.KAKAO, providerUserId = "kakao-9911"))

        service.confirmEmailChange(confirm, memberId)

        verify { memberRepository.saveEmail(memberFixture(id = memberId, provider = Provider.KAKAO, providerUserId = "kakao-9911", email = newEmail)) }
        assertEquals(listOf<Any>(EmailChanged(memberId, Provider.KAKAO, loginIdentifierUpdated = false)), events)
    }

    @Test
    fun `틀린 코드면 시도만 저장하고 이메일을 바꾸지 않는다`() {
        givenChallenge(challenge())
        givenMember(member())

        val result = service.confirmEmailChange(ConfirmEmailChangeCommand(publicId, "999999"), memberId)

        assertFalse(result.changed)
        assertNull(result.email)
        assertEquals("CODE_MISMATCH", result.failureReason)
        verify(exactly = 1) { memberRepository.saveEmailVerification(challenge().copy(attempts = 1)) }
        verify(exactly = 0) { memberRepository.saveEmail(any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `만료됐거나 이미 쓴 코드는 사유를 돌려주고 이메일을 바꾸지 않는다`() {
        givenMember(member())
        givenChallenge(challenge(expiresAt = now.minusSeconds(1)))
        val expired = service.confirmEmailChange(confirm, memberId)
        givenChallenge(challenge(consumed = true))
        val consumed = service.confirmEmailChange(confirm, memberId)

        assertEquals("EXPIRED", expired.failureReason)
        assertEquals("CONSUMED", consumed.failureReason)
        verify(exactly = 0) { memberRepository.saveEmail(any()) }
    }

    /** 핸들의 존재를 드러내지 않는다 — 남의 것·가입 인증용·없는 것 모두 같은 답이다. 코드는 맞는 값을 넣는다. */
    @Test
    fun `남의 핸들과 가입 인증 핸들과 없는 핸들은 모두 없는 요청으로 답한다`() {
        givenMember(member())
        val handles = listOf(
            challenge(owner = 99L),
            challenge(purpose = VerificationPurpose.REGISTRATION, targetEmail = null),
            challenge(purpose = VerificationPurpose.REGISTRATION, targetEmail = newEmail),
            null,
        )

        handles.forEach { handle ->
            givenChallenge(handle)
            val ex = assertFailsWith<VerificationNotFoundException> { service.confirmEmailChange(confirm, memberId) }
            assertEquals("유효하지 않은 검증 요청입니다.", ex.message)
            assertEquals("VERIFICATION_NOT_FOUND", ex.code)
        }
        verify(exactly = 0) { memberRepository.saveEmailVerification(any()) }
        verify(exactly = 0) { memberRepository.consumeEmailVerification(any()) }
        verify(exactly = 0) { memberRepository.saveEmail(any()) }
        verify(exactly = 0) { memberRepository.existsByEmailIdentifier(any(), any(), any()) }
        assertTrue(events.isEmpty())
    }

    /** 남의 코드에 시도 횟수를 남기면 제3자가 남의 변경 코드를 소진시킬 수 있다. */
    @Test
    fun `남의 핸들은 틀린 코드를 넣어도 시도를 남기지 않는다`() {
        givenMember(member())
        givenChallenge(challenge(owner = 99L))

        assertFailsWith<VerificationNotFoundException> { service.confirmEmailChange(ConfirmEmailChangeCommand(publicId, "999999"), memberId) }

        verify(exactly = 0) { memberRepository.saveEmailVerification(any()) }
        assertTrue(events.isEmpty())
    }

    /** 요청과 확인 사이에 다른 회원이 그 주소로 가입했을 수 있다. */
    @Test
    fun `확인 시점에 그 주소가 선점됐으면 이메일을 바꾸지 않는다`() {
        givenChallenge(challenge())
        givenMember(member())
        every { memberRepository.existsByEmailIdentifier(Provider.EMAIL, "protected:$newEmail", memberId) } returns true

        val ex = assertFailsWith<EmailAlreadyInUseException> { service.confirmEmailChange(confirm, memberId) }

        assertEquals("이미 사용 중인 이메일입니다.", ex.message)
        verify(exactly = 0) { memberRepository.saveEmail(any()) }
        assertTrue(events.isEmpty())
    }

    // --- 재발송 ---

    /** 대상 주소는 이전 코드에서 이어받는다 — 주소를 바꾸려면 변경 요청을 다시 한다. */
    @Test
    fun `쿨다운이 지났으면 이전 코드를 폐기하고 같은 주소로 다시 보낸다`() {
        val previous = challenge(createdAt = now.minusSeconds(61))
        givenChallenge(previous, forUpdate = false)
        givenMember(member())
        every { memberRepository.findEmailVerifications(memberId, VerificationPurpose.EMAIL_CHANGE) } returns listOf(previous)

        val result = service.resendEmailChange(ResendEmailChangeCommand(publicId), memberId)

        assertEquals(issued.captured.publicId, result.verificationPublicId)
        assertEquals(now.plus(policy.ttl), result.expiresAt)
        assertEquals(Email(newEmail), issued.captured.targetEmail)
        assertEquals(VerificationPurpose.EMAIL_CHANGE, issued.captured.purpose)
        verifyOrder {
            memberRepository.consumeEmailVerification(previous.copy(consumed = true))
            memberRepository.insertEmailVerification(any())
        }
        verify(exactly = 0) { memberRepository.saveEmailVerification(any()) }
        // 쿨다운은 이메일 변경 용도로만 센다 — 가입 인증의 발급 시각은 보지 않는다.
        verify(exactly = 0) { memberRepository.findEmailVerifications(any(), VerificationPurpose.REGISTRATION) }
        assertEquals(listOf<Any>(VerificationEmailRequested(newEmail, "123456")), events)
    }

    @Test
    fun `재발송은 쿨다운 안이면 RESEND_COOLDOWN_ACTIVE로 거부한다`() {
        val previous = challenge(createdAt = now.minusSeconds(30))
        givenChallenge(previous, forUpdate = false)
        givenMember(member())
        every { memberRepository.findEmailVerifications(memberId, VerificationPurpose.EMAIL_CHANGE) } returns listOf(previous)

        val ex = assertFailsWith<ResendCooldownActiveException> { service.resendEmailChange(ResendEmailChangeCommand(publicId), memberId) }

        assertEquals("잠시 후 다시 시도해 주세요.", ex.message)
        verify(exactly = 0) { memberRepository.consumeEmailVerification(any()) }
        verify(exactly = 0) { memberRepository.insertEmailVerification(any()) }
        assertTrue(events.isEmpty())
    }

    /** 쿨다운 안이어도 같은 답이다 — 쿨다운으로 답하면 남의 핸들이 있다는 것이 드러난다. */
    @Test
    fun `재발송도 남의 핸들과 가입 인증 핸들과 없는 핸들은 없는 요청으로 답한다`() {
        givenMember(member())
        every { memberRepository.findEmailVerifications(memberId, VerificationPurpose.EMAIL_CHANGE) } returns listOf(challenge(createdAt = now.minusSeconds(30)))
        val handles = listOf(
            challenge(owner = 99L),
            challenge(purpose = VerificationPurpose.REGISTRATION, targetEmail = null),
            null,
        )

        handles.forEach { handle ->
            givenChallenge(handle, forUpdate = false)
            val ex = assertFailsWith<VerificationNotFoundException> { service.resendEmailChange(ResendEmailChangeCommand(publicId), memberId) }
            assertEquals("유효하지 않은 검증 요청입니다.", ex.message)
        }
        verify(exactly = 0) { memberRepository.consumeEmailVerification(any()) }
        verify(exactly = 0) { memberRepository.saveEmailVerification(any()) }
        verify(exactly = 0) { memberRepository.insertEmailVerification(any()) }
        assertTrue(events.isEmpty())
    }
}
