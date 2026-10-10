package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.member.MemberFixtures.CODE
import org.around.ieuri.auth.domain.member.MemberFixtures.MEMBER_ID
import org.around.ieuri.auth.domain.member.MemberFixtures.NOW
import org.around.ieuri.auth.domain.member.MemberFixtures.POLICY
import org.around.ieuri.auth.domain.member.MemberFixtures.member
import org.around.ieuri.auth.domain.member.MemberFixtures.verification
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** 이메일 변경(SPEC-0011 R7·R8): 요청·재발송·확정. */
class MemberEmailChangeTest {

    private val next = Email("next@example.com")

    private fun change(n: Int = 1, consumed: Boolean = false, createdAt: Instant? = NOW.minusSeconds(3600), target: Email = next) = verification(
        n = n,
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

    @Test
    fun `대상 주소가 없는 코드로는 변경 코드를 재발송할 수 없다`() {
        val registration = verification(purpose = VerificationPurpose.REGISTRATION, targetEmail = null)

        assertFailsWith<IllegalArgumentException> { member().reissueEmailChange(registration, listOf(registration), CODE, POLICY, NOW) }
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
