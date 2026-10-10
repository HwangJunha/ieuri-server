package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.member.MemberFixtures.CODE
import org.around.ieuri.auth.domain.member.MemberFixtures.MEMBER_ID
import org.around.ieuri.auth.domain.member.MemberFixtures.NOW
import org.around.ieuri.auth.domain.member.MemberFixtures.POLICY
import org.around.ieuri.auth.domain.member.MemberFixtures.member
import org.around.ieuri.auth.domain.member.MemberFixtures.reset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 비밀번호 재설정: 발급·현재 코드 고르기·확인·교체. */
class MemberPasswordResetTest {

    private fun failure(reason: VerificationFailureReason) = VerificationResult.Failure(reason)

    @Test
    fun `재설정 코드는 정책값으로 만들어지고 쓸 수 있던 코드만 폐기한다`() {
        val active = reset(n = 1)
        val consumed = reset(n = 2, consumed = true)

        val issue = member().requestPasswordReset(listOf(active, consumed), CODE, POLICY, NOW)

        assertNull(issue.issued.id)
        assertEquals(CODE.publicId, issue.issued.publicId)
        assertEquals(MEMBER_ID, issue.issued.memberId)
        assertEquals("새-코드-해시", issue.issued.codeHash)
        assertEquals(NOW.plusSeconds(600), issue.issued.expiresAt)
        assertEquals(0, issue.issued.attempts)
        assertEquals(3, issue.issued.maxAttempts)
        assertFalse(issue.issued.consumed)
        assertNull(issue.issued.createdAt)
        assertEquals(listOf(active.copy(consumed = true)), issue.consumed)
    }

    @Test
    fun `비밀번호가 없는 SNS 회원은 쿨다운과 무관하게 재설정을 요청할 수 없다`() {
        val sns = member(provider = Provider.KAKAO, passwordHash = null)

        val e = assertFailsWith<NotEmailMemberException> { sns.requestPasswordReset(listOf(reset(createdAt = NOW)), CODE, POLICY, NOW) }

        assertEquals("SNS로 가입한 계정입니다. 해당 SNS로 로그인해 주세요.", e.message)
        assertEquals("NOT_EMAIL_MEMBER", e.code)
    }

    @Test
    fun `재설정 쿨다운은 폐기된 코드까지 세고 정확히 지난 순간부터 풀린다`() {
        val justBefore = reset(n = 1, consumed = true, createdAt = NOW.minusSeconds(59))
        val exactly = reset(n = 2, createdAt = NOW.minusSeconds(60))

        val e = assertFailsWith<ResendCooldownActiveException> { member().requestPasswordReset(listOf(exactly, justBefore), CODE, POLICY, NOW) }

        assertEquals("잠시 후 다시 시도해 주세요.", e.message)
        assertEquals("RESEND_COOLDOWN_ACTIVE", e.code)
        assertEquals(CODE.publicId, member().requestPasswordReset(listOf(exactly), CODE, POLICY, NOW).issued.publicId)
        assertEquals(CODE.publicId, member().requestPasswordReset(emptyList(), CODE, POLICY, NOW).issued.publicId)
    }

    @Test
    fun `지금 쓸 코드는 소진되지 않은 것 중 가장 최근 것이다`() {
        val older = reset(n = 1, createdAt = NOW.minusSeconds(300))
        val newer = reset(n = 2, createdAt = NOW.minusSeconds(200))
        val newestButConsumed = reset(n = 3, consumed = true, createdAt = NOW.minusSeconds(100))

        assertEquals(newer.publicId, member().activePasswordReset(listOf(older, newestButConsumed, newer))?.publicId)
        assertEquals(newer.publicId, member().activePasswordReset(listOf(newer, older))?.publicId)
    }

    @Test
    fun `쓸 수 있는 코드가 없으면 null이다`() {
        assertNull(member().activePasswordReset(emptyList()))
        assertNull(member().activePasswordReset(listOf(reset(consumed = true))))
    }

    @Test
    fun `코드가 맞으면 소진되고 통과한다`() {
        val attempt = member().verifyPasswordReset(reset(codeHash = "맞는-해시", attempts = 1), "맞는-해시", NOW)

        assertEquals(VerificationResult.Success, attempt.result)
        assertTrue(attempt.reset.consumed)
        assertEquals(1, attempt.reset.attempts)
    }

    @Test
    fun `코드가 틀리면 시도 횟수가 늘고 한도에 닿으면 소진된다`() {
        val first = member().verifyPasswordReset(reset(codeHash = "맞는-해시", attempts = 1, maxAttempts = 3), "틀린-해시", NOW)
        val last = member().verifyPasswordReset(reset(codeHash = "맞는-해시", attempts = 2, maxAttempts = 3), "틀린-해시", NOW)

        assertEquals(failure(VerificationFailureReason.CODE_MISMATCH), first.result)
        assertEquals(2, first.reset.attempts)
        assertFalse(first.reset.consumed)
        assertEquals(failure(VerificationFailureReason.CODE_MISMATCH), last.result)
        assertEquals(3, last.reset.attempts)
        assertTrue(last.reset.consumed)
    }

    @Test
    fun `시도를 다 쓴 코드는 맞는 코드를 넣어도 거부하고 소진한다`() {
        val attempt = member().verifyPasswordReset(reset(codeHash = "맞는-해시", attempts = 3, maxAttempts = 3), "맞는-해시", NOW)

        assertEquals(failure(VerificationFailureReason.MAX_ATTEMPTS_EXCEEDED), attempt.result)
        assertTrue(attempt.reset.consumed)
        assertEquals(3, attempt.reset.attempts)
    }

    @Test
    fun `만료 시각 정각까지는 유효하고 그 뒤로는 만료다`() {
        val reset = reset(codeHash = "맞는-해시", expiresAt = NOW)

        val after = member().verifyPasswordReset(reset, "맞는-해시", NOW.plusMillis(1))

        assertEquals(VerificationResult.Success, member().verifyPasswordReset(reset, "맞는-해시", NOW).result)
        assertEquals(failure(VerificationFailureReason.EXPIRED), after.result)
        assertEquals(reset, after.reset)
    }

    /** 실패 사유의 우선순위: CONSUMED → EXPIRED → MAX_ATTEMPTS_EXCEEDED → CODE_MISMATCH. */
    @Test
    fun `여러 사유가 겹치면 정해진 순서의 앞선 사유로 답한다`() {
        val expired = NOW.minusSeconds(1)
        val everything = reset(codeHash = "맞는-해시", consumed = true, expiresAt = expired, attempts = 3, maxAttempts = 3)
        val expiredAndExhausted = reset(codeHash = "맞는-해시", expiresAt = expired, attempts = 3, maxAttempts = 3)
        val exhaustedAndWrong = reset(codeHash = "맞는-해시", attempts = 3, maxAttempts = 3)

        val consumedAttempt = member().verifyPasswordReset(everything, "맞는-해시", NOW)
        val expiredAttempt = member().verifyPasswordReset(expiredAndExhausted, "틀린-해시", NOW)

        assertEquals(failure(VerificationFailureReason.CONSUMED), consumedAttempt.result)
        assertEquals(everything, consumedAttempt.reset)
        assertEquals(failure(VerificationFailureReason.EXPIRED), expiredAttempt.result)
        assertFalse(expiredAttempt.reset.consumed)
        assertEquals(failure(VerificationFailureReason.MAX_ATTEMPTS_EXCEEDED), member().verifyPasswordReset(exhaustedAndWrong, "틀린-해시", NOW).result)
    }

    @Test
    fun `비밀번호 교체는 해시만 바꾼다`() {
        val before = member(passwordHash = "hash-현재")

        val after = before.changePassword(PasswordHash("hash-새것"))

        assertEquals("hash-새것", after.passwordHash?.value)
        assertEquals(before, after.copy(passwordHash = before.passwordHash))
    }
}
