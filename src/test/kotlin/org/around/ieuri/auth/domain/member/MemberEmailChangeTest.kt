package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.member.MemberFixtures.CODE
import org.around.ieuri.auth.domain.member.MemberFixtures.MEMBER_ID
import org.around.ieuri.auth.domain.member.MemberFixtures.NOW
import org.around.ieuri.auth.domain.member.MemberFixtures.OTHER_MEMBER_ID
import org.around.ieuri.auth.domain.member.MemberFixtures.POLICY
import org.around.ieuri.auth.domain.member.MemberFixtures.member
import org.around.ieuri.auth.domain.member.MemberFixtures.verification
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 이메일 변경(SPEC-0011 R7·R8): 요청·재발송·확정. */
class MemberEmailChangeTest {

    private val next = Email("next@example.com")

    private fun change(
        n: Int = 1,
        consumed: Boolean = false,
        createdAt: Instant? = NOW.minusSeconds(3600),
        target: Email = next,
        memberId: Long = MEMBER_ID,
        codeHash: String = "해시-$n",
    ) = verification(
        n = n,
        memberId = memberId,
        codeHash = codeHash,
        consumed = consumed,
        createdAt = createdAt,
        purpose = VerificationPurpose.EMAIL_CHANGE,
        targetEmail = target,
    )

    @Test
    fun `변경 요청은 새 주소를 실은 변경 용도 코드를 낸다`() {
        val issue = member(provider = Provider.EMAIL).requestEmailChange(next, emptyList(), CODE, POLICY, NOW)

        assertNull(issue.issued.id)
        assertEquals(CODE.publicId, issue.issued.publicId)
        assertEquals(MEMBER_ID, issue.issued.memberId)
        assertEquals("새-코드-해시", issue.issued.codeHash)
        assertEquals(NOW.plusSeconds(600), issue.issued.expiresAt)
        assertEquals(3, issue.issued.maxAttempts)
        assertEquals(VerificationPurpose.EMAIL_CHANGE, issue.issued.purpose)
        assertEquals("next@example.com", issue.issued.targetEmail?.value)
        assertEquals(emptyList(), issue.consumed)
    }

    @Test
    fun `변경 요청은 진행 중이던 코드만 폐기한다`() {
        val active = change(n = 1)
        val consumed = change(n = 2, consumed = true)

        val issue = member().requestEmailChange(next, listOf(active, consumed), CODE, POLICY, NOW)

        assertEquals(listOf(active.copy(consumed = true)), issue.consumed)
    }

    @Test
    fun `SNS 회원은 이메일을 바꿀 수 없다`() {
        listOf(Provider.APPLE, Provider.GOOGLE, Provider.KAKAO).forEach { provider ->
            val sns = member(provider = provider, passwordHash = null)

            val e = assertFailsWith<NotEmailMemberException> { sns.requestEmailChange(next, emptyList(), CODE, POLICY, NOW) }

            assertEquals("SNS 계정은 이메일을 변경할 수 없습니다.", e.message)
            assertEquals("NOT_EMAIL_MEMBER", e.code)
            assertFailsWith<NotEmailMemberException> { sns.ensureCanChangeEmail(next) }
        }
    }

    @Test
    fun `지금과 같은 주소로는 바꿀 수 없다 - 대소문자와 공백은 같은 주소다`() {
        val member = member(email = "current@example.com")

        val e = assertFailsWith<SameAsCurrentEmailException> { member.requestEmailChange(Email("  Current@Example.com "), emptyList(), CODE, POLICY, NOW) }

        assertEquals("현재 사용 중인 이메일과 같습니다.", e.message)
        assertEquals("SAME_AS_CURRENT_EMAIL", e.code)
        assertFailsWith<SameAsCurrentEmailException> { member.ensureCanChangeEmail(Email("current@example.com")) }
        member.ensureCanChangeEmail(next)
    }

    @Test
    fun `SNS 회원이 같은 주소를 넣으면 회원 유형이 먼저 걸린다`() {
        val sns = member(provider = Provider.KAKAO, email = "current@example.com", passwordHash = null)

        assertFailsWith<NotEmailMemberException> { sns.ensureCanChangeEmail(Email("current@example.com")) }
    }

    @Test
    fun `변경 코드 재발송은 이전 코드의 대상 주소를 이어받는다`() {
        val previous = change(n = 1, target = Email("carried@example.com"))
        val consumed = change(n = 2, consumed = true)

        val issue = member().reissueEmailChange(previous, listOf(previous, consumed), CODE, POLICY, NOW)

        assertEquals("carried@example.com", issue.issued.targetEmail?.value)
        assertEquals(VerificationPurpose.EMAIL_CHANGE, issue.issued.purpose)
        assertEquals(CODE.publicId, issue.issued.publicId)
        assertEquals(listOf(previous.copy(consumed = true)), issue.consumed)
        assertFalse(issue.issued.consumed)
    }

    @Test
    fun `변경 코드 재발송 쿨다운은 정확히 지난 순간부터 풀린다`() {
        val justBefore = change(createdAt = NOW.minusSeconds(59))
        val exactly = change(createdAt = NOW.minusSeconds(60))

        val e = assertFailsWith<ResendCooldownActiveException> { member().reissueEmailChange(justBefore, listOf(justBefore), CODE, POLICY, NOW) }

        assertEquals("잠시 후 다시 시도해 주세요.", e.message)
        assertEquals("RESEND_COOLDOWN_ACTIVE", e.code)
        assertEquals(CODE.publicId, member().reissueEmailChange(exactly, listOf(exactly), CODE, POLICY, NOW).issued.publicId)
    }

    /** 대상 주소가 없는 코드는 가입 인증 코드뿐이고, 그것은 용도 판정에서 먼저 걸린다. */
    @Test
    fun `가입 인증 코드로는 변경 코드를 재발송할 수 없다 - 대상 주소가 있든 없든`() {
        val registration = verification(n = 1, purpose = VerificationPurpose.REGISTRATION, targetEmail = null)
        val retargeted = verification(n = 2, purpose = VerificationPurpose.REGISTRATION, targetEmail = Email("fixed@example.com"))

        val e = assertFailsWith<VerificationNotFoundException> { member().reissueEmailChange(registration, listOf(registration), CODE, POLICY, NOW) }

        assertEquals("유효하지 않은 검증 요청입니다.", e.message)
        assertEquals("VERIFICATION_NOT_FOUND", e.code)
        assertFailsWith<VerificationNotFoundException> { member().reissueEmailChange(retargeted, emptyList(), CODE, POLICY, NOW) }
    }

    /** 쿨다운도 함께 걸린 상태에서 없는 요청이 이긴다 — 쿨다운으로 답하면 남의 핸들이 있다는 것이 드러난다. */
    @Test
    fun `남의 코드와 가입 인증 코드로는 쿨다운 안이어도 없는 요청으로 답하며 재발송하지 않는다`() {
        val foreign = change(n = 1, memberId = OTHER_MEMBER_ID)
        val registration = verification(n = 2, purpose = VerificationPurpose.REGISTRATION, targetEmail = Email("fixed@example.com"))
        val justIssued = change(n = 3, createdAt = NOW.minusSeconds(10))

        val e = assertFailsWith<VerificationNotFoundException> { member().reissueEmailChange(foreign, listOf(justIssued), CODE, POLICY, NOW) }

        assertEquals("유효하지 않은 검증 요청입니다.", e.message)
        assertEquals("VERIFICATION_NOT_FOUND", e.code)
        assertFailsWith<VerificationNotFoundException> { member().reissueEmailChange(registration, listOf(justIssued), CODE, POLICY, NOW) }
        // 같은 상태에서 자기 변경 코드면 쿨다운에 걸린다 — 위 판정이 쿨다운보다 먼저였다는 뜻이다.
        assertFailsWith<ResendCooldownActiveException> { member().reissueEmailChange(justIssued, listOf(justIssued), CODE, POLICY, NOW) }
    }

    // ── 확인 ──

    /** 주소는 [Member.replaceEmail]이 바꾼다. 확인은 코드만 소진한다. */
    @Test
    fun `변경 코드가 맞으면 코드만 통과로 소진되고 회원은 그대로다`() {
        val member = member(emailVerified = false, email = "current@example.com")
        val code = change(codeHash = "맞는-해시")

        val attempt = member.confirmEmailChange(code, "맞는-해시", NOW)

        assertEquals(VerificationResult.Success, attempt.result)
        assertEquals(NOW, attempt.verification.verifiedAt)
        assertTrue(attempt.verification.consumed)
        assertEquals(0, attempt.verification.attempts)
        assertSame(member, attempt.member)
        assertFalse(attempt.member.emailVerified)
        assertEquals("current@example.com", attempt.member.email.value)
    }

    @Test
    fun `변경 코드가 틀리면 시도 횟수가 늘고 회원은 그대로다`() {
        val member = member()

        val attempt = member.confirmEmailChange(change(codeHash = "맞는-해시"), "틀린-해시", NOW)

        assertEquals(VerificationResult.Failure(VerificationFailureReason.CODE_MISMATCH), attempt.result)
        assertEquals(1, attempt.verification.attempts)
        assertFalse(attempt.verification.consumed)
        assertNull(attempt.verification.verifiedAt)
        assertSame(member, attempt.member)
    }

    @Test
    fun `남의 변경 코드는 해시가 맞아도 없는 요청으로 답한다`() {
        val foreign = change(memberId = OTHER_MEMBER_ID, codeHash = "맞는-해시")

        val e = assertFailsWith<VerificationNotFoundException> { member().confirmEmailChange(foreign, "맞는-해시", NOW) }

        assertEquals("유효하지 않은 검증 요청입니다.", e.message)
        assertEquals("VERIFICATION_NOT_FOUND", e.code)
    }

    /** 정정한 주소를 실은 가입 인증 코드도 대상 주소가 있다 — 용도로 걸러야 인증 없이 주소가 바뀌지 않는다. */
    @Test
    fun `가입 인증 코드는 해시가 맞아도 이메일 변경 확인에서 없는 요청으로 답한다`() {
        val plain = verification(n = 1, codeHash = "맞는-해시", purpose = VerificationPurpose.REGISTRATION, targetEmail = null)
        val retargeted = verification(n = 2, codeHash = "맞는-해시", purpose = VerificationPurpose.REGISTRATION, targetEmail = Email("fixed@example.com"))

        val e = assertFailsWith<VerificationNotFoundException> { member().confirmEmailChange(plain, "맞는-해시", NOW) }

        assertEquals("유효하지 않은 검증 요청입니다.", e.message)
        assertEquals("VERIFICATION_NOT_FOUND", e.code)
        assertFailsWith<VerificationNotFoundException> { member().confirmEmailChange(retargeted, "맞는-해시", NOW) }
    }

    @Test
    fun `자체 회원은 이메일과 로그인 식별자가 함께 바뀐다`() {
        val before = member(provider = Provider.EMAIL, providerUserId = "login-id-현재", email = "current@example.com")

        val after = before.replaceEmail(next, ProviderUserId("login-id-새것"))

        assertEquals("next@example.com", after.email.value)
        assertEquals("login-id-새것", after.providerUserId.value)
        assertEquals(before, after.copy(email = before.email, providerUserId = before.providerUserId))
    }

    @Test
    fun `SNS 회원은 이메일만 바뀌고 로그인 식별자는 그대로다`() {
        val before = member(provider = Provider.GOOGLE, providerUserId = "google-sub", email = "current@example.com", passwordHash = null)

        val after = before.replaceEmail(next, ProviderUserId("login-id-새것"))

        assertEquals("next@example.com", after.email.value)
        assertEquals("google-sub", after.providerUserId.value)
    }
}
