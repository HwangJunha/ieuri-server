package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.application.member.required.MemberSessionPort
import org.around.ieuri.auth.application.member.required.PasswordEncoderPort
import org.around.ieuri.auth.application.member.required.VerificationCodeGeneratorPort
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.NotEmailMemberException
import org.around.ieuri.auth.domain.member.PasswordReset
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.auth.domain.member.ResendCooldownActiveException
import org.around.ieuri.auth.domain.member.VerificationPolicy
import org.around.ieuri.auth.domain.member.event.PasswordResetCompleted
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 비밀번호 재설정.
 * - 실행 단계의 실패 사유는 `CODE_MISMATCH` 하나로 모은다 — 갈리면 인증 없이 계정을 열거할 수 있다.
 * - 코드 확인 → 비밀번호 저장 → 세션 폐기 순서를 고정한다.
 */
class PasswordResetServiceTest {

    private val now = Instant.parse("2026-07-16T00:00:00Z")
    private val policy = VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60))

    private val memberRepository = mockk<MemberRepository>(relaxUnitFun = true)
    private val sessions = mockk<MemberSessionPort>(relaxUnitFun = true)
    private val codeGenerator = mockk<VerificationCodeGeneratorPort> {
        every { generate() } returns "123456"
        every { hash(any()) } answers { "hashed:${firstArg<String>()}" }
    }
    private val passwordEncoder = mockk<PasswordEncoderPort> {
        every { encode(any()) } answers { "encoded:${firstArg<String>()}" }
    }
    private val eventPublisher = mockk<MemberEventPublisherPort>()
    private val events = mutableListOf<Any>()

    private val service = PasswordResetService(
        memberRepository,
        sessions,
        codeGenerator,
        passwordEncoder,
        prefixingIdentifierProtector(),
        eventPublisher,
        policy,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private val loginIdentifier = ProviderUserId("protected:user@example.com")
    private fun emailMember(passwordHash: String? = "old-hash") = memberFixture(id = 300L, provider = Provider.EMAIL, providerUserId = "protected:user@example.com", passwordHash = passwordHash)

    init {
        every { eventPublisher.publish(capture(events)) } just runs
        every { memberRepository.findByProviderUserId(any(), any()) } returns null
        every { memberRepository.findPasswordResets(any(), any()) } returns emptyList()
    }

    private fun givenMember(member: Member?) {
        every { memberRepository.findByProviderUserId(Provider.EMAIL, loginIdentifier) } returns member
    }

    private fun reset(
        id: Long = 1L,
        codeHash: String = "hashed:123456",
        consumed: Boolean = false,
        createdAt: Instant? = now.minusSeconds(120),
        expiresAt: Instant = now.plusSeconds(480),
    ) = passwordResetFixture(id = id, codeHash = codeHash, consumed = consumed, createdAt = createdAt, expiresAt = expiresAt)

    // --- 코드 발송 요청 ---

    @Test
    fun `재설정 요청은 쓰던 코드를 폐기하고 새 코드를 넣은 뒤 평문 코드를 이벤트로 내보낸다`() {
        val active = reset(id = 1L)
        val used = reset(id = 2L, consumed = true, createdAt = now.minusSeconds(900))
        givenMember(emailMember())
        every { memberRepository.findPasswordResets(300L, forUpdate = false) } returns listOf(used, active)
        val issued = slot<PasswordReset>()
        every { memberRepository.insertPasswordReset(capture(issued)) } just runs

        // 정규화(trim+lowercase) → 보호 변환 순서라 표기가 달라도 같은 회원을 찾는다.
        service.requestPasswordReset(RequestPasswordResetCommand("  USER@Example.COM "))

        // 폐기는 consumed 한 열만 쓰는 연산으로 한다. 이미 소진된 코드는 다시 쓰지 않는다.
        verify(exactly = 1) { memberRepository.consumePasswordReset(any()) }
        verify(exactly = 0) { memberRepository.savePasswordReset(any()) }
        verifyOrder {
            memberRepository.consumePasswordReset(active.copy(consumed = true))
            memberRepository.insertPasswordReset(any())
        }
        assertNull(issued.captured.id)
        assertEquals(300L, issued.captured.memberId)
        assertEquals("hashed:123456", issued.captured.codeHash)
        assertEquals(now.plus(policy.ttl), issued.captured.expiresAt)
        assertEquals(policy.maxAttempts, issued.captured.maxAttempts)
        assertFalse(issued.captured.consumed)
        assertEquals(listOf<Any>(PasswordResetRequested(memberId = 300L, toEmail = "user@example.com", code = "123456")), events)
    }

    @Test
    fun `가입되지 않은 주소의 재설정 요청은 MEMBER_NOT_FOUND로 거부한다`() {
        val ex = assertFailsWith<MemberNotFoundException> { service.requestPasswordReset(RequestPasswordResetCommand("ghost@example.com")) }

        assertEquals("가입되지 않은 이메일입니다.", ex.message)
        verify(exactly = 0) { memberRepository.insertPasswordReset(any()) }
        assertTrue(events.isEmpty())
    }

    /** 재설정할 비밀번호가 없는 계정. 발송하지 않고 코드로 알린다. */
    @Test
    fun `비밀번호가 없는 계정의 재설정 요청은 NOT_EMAIL_MEMBER로 거부한다`() {
        givenMember(emailMember(passwordHash = null))

        val ex = assertFailsWith<NotEmailMemberException> { service.requestPasswordReset(RequestPasswordResetCommand("user@example.com")) }

        assertEquals("NOT_EMAIL_MEMBER", ex.code)
        assertEquals("SNS로 가입한 계정입니다. 해당 SNS로 로그인해 주세요.", ex.message)
        verify(exactly = 0) { memberRepository.insertPasswordReset(any()) }
        assertTrue(events.isEmpty())
    }

    /** 쿨다운이 없으면 주소를 아는 제3자가 메일함에 코드를 무제한 보낼 수 있다. */
    @Test
    fun `재설정 요청은 쿨다운 안이면 RESEND_COOLDOWN_ACTIVE로 거부한다`() {
        givenMember(emailMember())
        every { memberRepository.findPasswordResets(300L, forUpdate = false) } returns listOf(reset(createdAt = now.minusSeconds(30)))

        val ex = assertFailsWith<ResendCooldownActiveException> { service.requestPasswordReset(RequestPasswordResetCommand("user@example.com")) }

        assertEquals("잠시 후 다시 시도해 주세요.", ex.message)
        verify(exactly = 0) { memberRepository.consumePasswordReset(any()) }
        verify(exactly = 0) { memberRepository.savePasswordReset(any()) }
        verify(exactly = 0) { memberRepository.insertPasswordReset(any()) }
        assertTrue(events.isEmpty())
    }

    // --- 코드 확인과 교체 ---

    private val command = ResetPasswordCommand("user@example.com", "123456", "NewPassw0rd!")

    private fun verifyNothingWritten() {
        verify(exactly = 0) { memberRepository.savePasswordReset(any()) }
        verify(exactly = 0) { memberRepository.consumePasswordReset(any()) }
        verify(exactly = 0) { memberRepository.savePassword(any()) }
        verify(exactly = 0) { sessions.revokeAll(any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `없는 계정의 재설정 실행은 CODE_MISMATCH로 끝나고 아무것도 쓰지 않는다`() {
        val result = service.resetPassword(command)

        assertFalse(result.success)
        assertEquals("CODE_MISMATCH", result.failureReason)
        verify(exactly = 0) { memberRepository.findPasswordResets(any(), any()) }
        verifyNothingWritten()
    }

    @Test
    fun `비밀번호가 없는 계정의 재설정 실행은 CODE_MISMATCH로 끝나고 코드를 읽지도 않는다`() {
        givenMember(emailMember(passwordHash = null))

        val result = service.resetPassword(command)

        assertFalse(result.success)
        assertEquals("CODE_MISMATCH", result.failureReason)
        verify(exactly = 0) { memberRepository.findPasswordResets(any(), any()) }
        verifyNothingWritten()
    }

    @Test
    fun `쓸 수 있는 코드가 없으면 CODE_MISMATCH로 끝나고 아무것도 쓰지 않는다`() {
        givenMember(emailMember())
        every { memberRepository.findPasswordResets(300L, forUpdate = true) } returns listOf(reset(consumed = true))

        val result = service.resetPassword(command)

        assertFalse(result.success)
        assertEquals("CODE_MISMATCH", result.failureReason)
        verifyNothingWritten()
    }

    @Test
    fun `코드가 틀리면 시도만 저장하고 비밀번호와 세션은 그대로 둔다`() {
        val active = reset(codeHash = "hashed:654321")
        givenMember(emailMember())
        every { memberRepository.findPasswordResets(300L, forUpdate = true) } returns listOf(active)

        val result = service.resetPassword(command)

        assertFalse(result.success)
        assertEquals("CODE_MISMATCH", result.failureReason)
        verify(exactly = 1) { memberRepository.savePasswordReset(active.copy(attempts = 1)) }
        verify(exactly = 0) { passwordEncoder.encode(any()) }
        verify(exactly = 0) { memberRepository.savePassword(any()) }
        verify(exactly = 0) { sessions.revokeAll(any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `만료된 코드는 맞는 값이어도 EXPIRED로 실패한다`() {
        givenMember(emailMember())
        every { memberRepository.findPasswordResets(300L, forUpdate = true) } returns listOf(reset(expiresAt = now.minusSeconds(1)))

        val result = service.resetPassword(command)

        assertEquals("EXPIRED", result.failureReason)
        verify(exactly = 0) { memberRepository.savePassword(any()) }
        verify(exactly = 0) { sessions.revokeAll(any()) }
    }

    /** 세션 폐기는 코드 확인과 비밀번호 저장 뒤다. */
    @Test
    fun `코드가 맞으면 소진 저장 → 비밀번호 저장 → 세션 폐기 → 완료 이벤트 순으로 한다`() {
        val active = reset()
        givenMember(emailMember())
        every { memberRepository.findPasswordResets(300L, forUpdate = true) } returns listOf(active)

        val result = service.resetPassword(command)

        assertTrue(result.success)
        assertNull(result.failureReason)
        verifyOrder {
            memberRepository.savePasswordReset(active.copy(consumed = true))
            memberRepository.savePassword(emailMember(passwordHash = "encoded:NewPassw0rd!"))
            sessions.revokeAll(300L)
            eventPublisher.publish(PasswordResetCompleted(300L))
        }
        assertEquals(listOf<Any>(PasswordResetCompleted(300L)), events)
    }

    /** 소진되지 않은 것 중 가장 최근 코드로만 확인한다. */
    @Test
    fun `살아 있는 코드가 여럿이면 가장 최근 것으로 확인한다`() {
        val older = reset(id = 1L, codeHash = "hashed:123456", createdAt = now.minusSeconds(300))
        val latest = reset(id = 2L, codeHash = "hashed:999999", createdAt = now.minusSeconds(100))
        givenMember(emailMember())
        every { memberRepository.findPasswordResets(300L, forUpdate = true) } returns listOf(older, latest)

        val result = service.resetPassword(command)

        // 입력은 이전 코드와 맞지만 대조 대상은 최신 코드다.
        assertEquals("CODE_MISMATCH", result.failureReason)
        verify(exactly = 1) { memberRepository.savePasswordReset(latest.copy(attempts = 1)) }
    }

    /** 폐기가 실패하면 예외가 나가 트랜잭션이 되돌아간다 — 완료 이벤트는 내지 않는다. */
    @Test
    fun `세션 폐기가 실패하면 예외를 그대로 내보내고 완료 이벤트를 내지 않는다`() {
        givenMember(emailMember())
        every { memberRepository.findPasswordResets(300L, forUpdate = true) } returns listOf(reset())
        every { sessions.revokeAll(300L) } throws IllegalStateException("세션 저장소 장애")

        assertFailsWith<IllegalStateException> { service.resetPassword(command) }

        assertTrue(events.isEmpty())
    }
}
