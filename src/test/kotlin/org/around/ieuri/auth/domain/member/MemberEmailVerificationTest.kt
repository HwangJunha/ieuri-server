package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.member.MemberFixtures.CODE
import org.around.ieuri.auth.domain.member.MemberFixtures.MEMBER_ID
import org.around.ieuri.auth.domain.member.MemberFixtures.NOW
import org.around.ieuri.auth.domain.member.MemberFixtures.POLICY
import org.around.ieuri.auth.domain.member.MemberFixtures.member
import org.around.ieuri.auth.domain.member.MemberFixtures.verification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 가입 이메일 인증: 발급·재발송·주소 정정·확인. 실패 사유 이름은 응답의 failureReason이다. */
class MemberEmailVerificationTest {

    private fun failure(reason: VerificationFailureReason) = VerificationResult.Failure(reason)

    // ── 발급 ──

    @Test
    fun `첫 인증 코드는 정책값으로 만들어진다`() {
        val issued = member().issueEmailVerification(CODE, POLICY, NOW)

        assertNull(issued.id)
        assertEquals(CODE.publicId, issued.publicId)
        assertEquals(MEMBER_ID, issued.memberId)
        assertEquals("새-코드-해시", issued.codeHash)
        assertEquals(NOW.plusSeconds(600), issued.expiresAt)
        assertEquals(0, issued.attempts)
        assertEquals(3, issued.maxAttempts)
        assertFalse(issued.consumed)
        assertNull(issued.verifiedAt)
        assertNull(issued.createdAt)
        assertEquals(VerificationPurpose.REGISTRATION, issued.purpose)
        assertNull(issued.targetEmail)
        assertTrue(issued.active)
    }

    // ── 재발송 ──

    @Test
    fun `재발송은 쓸 수 있던 코드만 폐기하고 새 코드를 낸다`() {
        val active = verification(n = 1)
        val consumed = verification(n = 2, consumed = true)
        val verified = verification(n = 3, verifiedAt = NOW.minusSeconds(100))

        val issue = member().reissueEmailVerification(active, listOf(active, consumed, verified), CODE, POLICY, NOW)

        assertEquals(listOf(active.publicId), issue.consumed.map { it.publicId })
        assertTrue(issue.consumed.single().consumed)
        assertEquals(active.copy(consumed = true), issue.consumed.single())
        assertEquals(CODE.publicId, issue.issued.publicId)
        assertEquals("새-코드-해시", issue.issued.codeHash)
        assertEquals(NOW.plusSeconds(600), issue.issued.expiresAt)
        assertEquals(VerificationPurpose.REGISTRATION, issue.issued.purpose)
        assertFalse(issue.issued.consumed)
    }

    @Test
    fun `재발송은 정정한 대기 주소를 이어받는다`() {
        val retargeted = verification(targetEmail = Email("fixed@example.com"))
        val plain = verification(n = 2)

        assertEquals("fixed@example.com", member().reissueEmailVerification(retargeted, listOf(retargeted), CODE, POLICY, NOW).issued.targetEmail?.value)
        assertNull(member().reissueEmailVerification(plain, listOf(plain), CODE, POLICY, NOW).issued.targetEmail)
    }

    @Test
    fun `재발송 쿨다운은 정확히 지난 순간부터 풀린다`() {
        val justBefore = verification(createdAt = NOW.minusSeconds(59))
        val exactly = verification(createdAt = NOW.minusSeconds(60))

        val e = assertFailsWith<VerificationRateLimitedException> {
            member().reissueEmailVerification(justBefore, listOf(justBefore), CODE, POLICY, NOW)
        }
        assertEquals("잠시 후 다시 시도해 주세요.", e.message)
        assertEquals("VERIFICATION_RATE_LIMITED", e.code)
        assertEquals(CODE.publicId, member().reissueEmailVerification(exactly, listOf(exactly), CODE, POLICY, NOW).issued.publicId)
    }

    @Test
    fun `쿨다운은 폐기된 코드까지 포함해 가장 최근 발급 시각으로 센다`() {
        val old = verification(n = 1, createdAt = NOW.minusSeconds(3600))
        val recentConsumed = verification(n = 2, consumed = true, createdAt = NOW.minusSeconds(10))

        assertFailsWith<VerificationRateLimitedException> {
            member().reissueEmailVerification(old, listOf(old, recentConsumed), CODE, POLICY, NOW)
        }
    }

    // ── 주소 정정 ──

    @Test
    fun `주소 정정은 새 주소를 코드에 싣고 회원의 이메일은 그대로 둔다`() {
        val active = verification(n = 1)
        val consumed = verification(n = 2, consumed = true)
        val member = member(email = "current@example.com")

        val issue = member.retargetEmailVerification(Email("fixed@example.com"), listOf(active, consumed), CODE, POLICY, NOW)

        assertEquals("fixed@example.com", issue.issued.targetEmail?.value)
        assertEquals(VerificationPurpose.REGISTRATION, issue.issued.purpose)
        assertEquals(CODE.publicId, issue.issued.publicId)
        assertEquals(listOf(active.copy(consumed = true)), issue.consumed)
        assertEquals("current@example.com", member.email.value)
    }

    @Test
    fun `주소 정정의 쿨다운은 재발송과 다른 예외다`() {
        val justBefore = verification(createdAt = NOW.minusSeconds(59))
        val exactly = verification(createdAt = NOW.minusSeconds(60))

        val e = assertFailsWith<ResendCooldownActiveException> {
            member().retargetEmailVerification(Email("fixed@example.com"), listOf(justBefore), CODE, POLICY, NOW)
        }
        assertEquals("잠시 후 다시 시도해 주세요.", e.message)
        assertEquals("RESEND_COOLDOWN_ACTIVE", e.code)
        assertEquals("fixed@example.com", member().retargetEmailVerification(Email("fixed@example.com"), listOf(exactly), CODE, POLICY, NOW).issued.targetEmail?.value)
    }

    @Test
    fun `인증을 마친 회원은 쿨다운과 무관하게 주소를 정정할 수 없다`() {
        val verified = member(emailVerified = true)

        val e = assertFailsWith<AlreadyVerifiedException> {
            verified.retargetEmailVerification(Email("fixed@example.com"), listOf(verification(createdAt = NOW)), CODE, POLICY, NOW)
        }
        assertEquals("이미 이메일 인증을 마쳤습니다.", e.message)
        assertEquals("ALREADY_VERIFIED", e.code)
        assertFailsWith<AlreadyVerifiedException> { verified.ensureEmailUnverified() }
        member(emailVerified = false).ensureEmailUnverified()
    }

    // ── 확인 ──

    @Test
    fun `코드가 맞으면 통과 시각이 찍히고 소진되며 회원이 인증된다`() {
        val member = member(emailVerified = false)
        val verification = verification(codeHash = "맞는-해시", attempts = 1)

        val attempt = member.verifyEmail(verification, "맞는-해시", NOW)

        assertEquals(VerificationResult.Success, attempt.result)
        assertEquals(NOW, attempt.verification.verifiedAt)
        assertTrue(attempt.verification.consumed)
        assertEquals(1, attempt.verification.attempts)
        assertFalse(attempt.verification.active)
        assertTrue(attempt.member.emailVerified)
        assertEquals(member.copy(emailVerified = true), attempt.member)
    }

    @Test
    fun `이메일 변경 코드는 통과해도 회원의 인증 여부를 바꾸지 않는다`() {
        val member = member(emailVerified = false)
        val change = verification(codeHash = "맞는-해시", purpose = VerificationPurpose.EMAIL_CHANGE, targetEmail = Email("next@example.com"))

        val attempt = member.verifyEmail(change, "맞는-해시", NOW)

        assertEquals(VerificationResult.Success, attempt.result)
        assertEquals(NOW, attempt.verification.verifiedAt)
        assertSame(member, attempt.member)
        assertFalse(attempt.member.emailVerified)
    }

    @Test
    fun `코드가 틀리면 시도 횟수가 늘고 회원은 그대로다`() {
        val member = member(emailVerified = false)

        val attempt = member.verifyEmail(verification(codeHash = "맞는-해시", attempts = 0, maxAttempts = 3), "틀린-해시", NOW)

        assertEquals(failure(VerificationFailureReason.CODE_MISMATCH), attempt.result)
        assertEquals(1, attempt.verification.attempts)
        assertFalse(attempt.verification.consumed)
        assertNull(attempt.verification.verifiedAt)
        assertSame(member, attempt.member)
    }

    @Test
    fun `틀린 시도가 한도에 닿으면 코드가 소진된다`() {
        val oneLeft = member().verifyEmail(verification(codeHash = "맞는-해시", attempts = 1, maxAttempts = 3), "틀린-해시", NOW)
        val last = member().verifyEmail(verification(codeHash = "맞는-해시", attempts = 2, maxAttempts = 3), "틀린-해시", NOW)

        assertEquals(2, oneLeft.verification.attempts)
        assertFalse(oneLeft.verification.consumed)
        assertEquals(failure(VerificationFailureReason.CODE_MISMATCH), last.result)
        assertEquals(3, last.verification.attempts)
        assertTrue(last.verification.consumed)
    }

    @Test
    fun `시도를 다 쓴 코드는 맞는 코드를 넣어도 거부하고 소진한다`() {
        val member = member(emailVerified = false)

        val attempt = member.verifyEmail(verification(codeHash = "맞는-해시", attempts = 3, maxAttempts = 3), "맞는-해시", NOW)

        assertEquals(failure(VerificationFailureReason.MAX_ATTEMPTS_EXCEEDED), attempt.result)
        assertTrue(attempt.verification.consumed)
        assertEquals(3, attempt.verification.attempts)
        assertNull(attempt.verification.verifiedAt)
        assertFalse(attempt.member.emailVerified)
    }

    @Test
    fun `만료 시각 정각까지는 유효하고 그 뒤로는 만료다`() {
        val member = member(emailVerified = false)
        val verification = verification(codeHash = "맞는-해시", expiresAt = NOW)

        val atExpiry = member.verifyEmail(verification, "맞는-해시", NOW)
        val after = member.verifyEmail(verification, "맞는-해시", NOW.plusMillis(1))

        assertEquals(VerificationResult.Success, atExpiry.result)
        assertEquals(failure(VerificationFailureReason.EXPIRED), after.result)
        assertEquals(verification, after.verification)
        assertFalse(after.member.emailVerified)
    }

    @Test
    fun `소진됐거나 이미 통과한 코드는 맞는 코드여도 CONSUMED다`() {
        val consumed = verification(n = 1, codeHash = "맞는-해시", consumed = true)
        val verified = verification(n = 2, codeHash = "맞는-해시", verifiedAt = NOW.minusSeconds(5))

        val first = member().verifyEmail(consumed, "맞는-해시", NOW)
        val second = member().verifyEmail(verified, "맞는-해시", NOW)

        assertEquals(failure(VerificationFailureReason.CONSUMED), first.result)
        assertEquals(consumed, first.verification)
        assertEquals(failure(VerificationFailureReason.CONSUMED), second.result)
        assertEquals(verified, second.verification)
        assertFalse(first.member.emailVerified)
    }

    /** 실패 사유의 우선순위: CONSUMED → EXPIRED → MAX_ATTEMPTS_EXCEEDED → CODE_MISMATCH. */
    @Test
    fun `여러 사유가 겹치면 정해진 순서의 앞선 사유로 답한다`() {
        val expired = NOW.minusSeconds(1)
        val everything = verification(codeHash = "맞는-해시", consumed = true, expiresAt = expired, attempts = 3, maxAttempts = 3)
        val expiredAndExhausted = verification(codeHash = "맞는-해시", expiresAt = expired, attempts = 3, maxAttempts = 3)
        val exhaustedAndWrong = verification(codeHash = "맞는-해시", attempts = 3, maxAttempts = 3)

        val expiredAttempt = member().verifyEmail(expiredAndExhausted, "틀린-해시", NOW)
        val exhaustedAttempt = member().verifyEmail(exhaustedAndWrong, "틀린-해시", NOW)

        assertEquals(failure(VerificationFailureReason.CONSUMED), member().verifyEmail(everything, "틀린-해시", NOW).result)
        assertEquals(failure(VerificationFailureReason.EXPIRED), expiredAttempt.result)
        assertFalse(expiredAttempt.verification.consumed)
        assertEquals(failure(VerificationFailureReason.MAX_ATTEMPTS_EXCEEDED), exhaustedAttempt.result)
        assertEquals(3, exhaustedAttempt.verification.attempts)
    }

    @Test
    fun `이메일 변경 코드는 대상 주소 없이 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { verification(purpose = VerificationPurpose.EMAIL_CHANGE, targetEmail = null) }
    }
}
