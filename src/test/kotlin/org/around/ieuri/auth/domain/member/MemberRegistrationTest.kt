package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.member.MemberFixtures.ACTIVE_TERMS
import org.around.ieuri.auth.domain.member.MemberFixtures.NOW
import org.around.ieuri.auth.domain.member.MemberFixtures.member
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 가입 규칙(SPEC-0024 R2·R3·R12)과 가입 때 루트가 만드는 자식들. 예외 메시지는 응답에 그대로 나간다. */
class MemberRegistrationTest {

    private val required = setOf("SERVICE", "PRIVACY")

    private fun agreement(code: String, agreed: Boolean) = Agreement(termsId = 1L, termsCode = code, termsVersion = "1.0", agreed = agreed, agreedAt = NOW)

    private val allAgreed = listOf(agreement("SERVICE", true), agreement("PRIVACY", true))

    private fun registerSns(
        providerEmail: Email? = Email("provider@example.com"),
        ageVerified: Boolean = true,
        agreements: List<Agreement> = allAgreed,
        requiredCodes: Set<String> = required,
    ) = Member.register(
        provider = Provider.KAKAO,
        providerUserId = ProviderUserId("kakao-123"),
        providerEmail = providerEmail,
        fallbackEmail = Email("typed@example.com"),
        ageVerified = ageVerified,
        agreements = agreements,
        requiredTermsCodes = requiredCodes,
    )

    private fun registerEmail(ageVerified: Boolean = true, agreements: List<Agreement> = allAgreed) = Member.registerByEmail(
        email = Email("self@example.com"),
        providerUserId = ProviderUserId("hmac-값"),
        passwordHash = PasswordHash("hash-값"),
        ageVerified = ageVerified,
        agreements = agreements,
        requiredTermsCodes = required,
    )

    @Test
    fun `provider가 이메일을 주면 그 주소로 가입하고 인증이 면제된다`() {
        val member = registerSns()

        assertNull(member.id)
        assertEquals(Provider.KAKAO, member.provider)
        assertEquals("kakao-123", member.providerUserId.value)
        assertEquals("provider@example.com", member.email.value)
        assertTrue(member.emailVerified)
        assertEquals(MemberStatus.ACTIVE, member.status)
        assertTrue(member.ageVerified)
        assertNull(member.suspendedAt)
        assertNull(member.suspendedUntil)
        assertNull(member.suspensionReason)
        assertNull(member.passwordHash)
        assertFalse(member.hasPassword)
    }

    @Test
    fun `provider가 이메일을 주지 않으면 입력한 주소로 가입하고 미인증으로 시작한다`() {
        val member = registerSns(providerEmail = null)

        assertEquals("typed@example.com", member.email.value)
        assertFalse(member.emailVerified)
    }

    @Test
    fun `자체 가입은 EMAIL 회원으로 미인증에 비밀번호 해시를 갖고 시작한다`() {
        val member = registerEmail()

        assertNull(member.id)
        assertEquals(Provider.EMAIL, member.provider)
        assertEquals("hmac-값", member.providerUserId.value)
        assertEquals("self@example.com", member.email.value)
        assertFalse(member.emailVerified)
        assertEquals(MemberStatus.ACTIVE, member.status)
        assertTrue(member.ageVerified)
        assertEquals("hash-값", member.passwordHash?.value)
        assertTrue(member.hasPassword)
    }

    @Test
    fun `만 14세 확인이 없으면 가입을 거부한다`() {
        val sns = assertFailsWith<UnderageRegistrationException> { registerSns(ageVerified = false) }
        val self = assertFailsWith<UnderageRegistrationException> { registerEmail(ageVerified = false) }

        assertEquals("만 14세 미만은 가입할 수 없습니다.", sns.message)
        assertEquals("INVALID_INPUT", sns.code)
        assertEquals("만 14세 미만은 가입할 수 없습니다.", self.message)
    }

    @Test
    fun `나이와 약관이 모두 어긋나면 나이가 먼저 걸린다`() {
        assertFailsWith<UnderageRegistrationException> { registerSns(ageVerified = false, agreements = emptyList()) }
    }

    @Test
    fun `필수 약관이 빠지면 미동의 코드를 메시지 끝에 붙여 거부한다`() {
        val e = assertFailsWith<RequiredTermsNotAgreedException> { registerSns(agreements = listOf(agreement("SERVICE", true))) }

        assertEquals("필수 약관에 동의해야 가입할 수 있습니다. 미동의: [PRIVACY]", e.message)
        assertEquals("INVALID_INPUT", e.code)
    }

    @Test
    fun `비동의로 제출한 필수 약관도 미동의다`() {
        val e = assertFailsWith<RequiredTermsNotAgreedException> {
            registerEmail(agreements = listOf(agreement("SERVICE", false), agreement("PRIVACY", true)))
        }

        assertEquals("필수 약관에 동의해야 가입할 수 있습니다. 미동의: [SERVICE]", e.message)
    }

    @Test
    fun `미동의 코드가 여럿이면 필수 약관 순서대로 나열한다`() {
        val e = assertFailsWith<RequiredTermsNotAgreedException> {
            registerSns(agreements = listOf(agreement("PRIVACY", true)), requiredCodes = linkedSetOf("SERVICE", "PRIVACY", "LOCATION"))
        }

        assertEquals("필수 약관에 동의해야 가입할 수 있습니다. 미동의: [SERVICE, LOCATION]", e.message)
    }

    @Test
    fun `선택 약관은 비동의여도 가입된다`() {
        val member = registerSns(agreements = allAgreed + agreement("MARKETING", false))

        assertEquals(MemberStatus.ACTIVE, member.status)
    }

    @Test
    fun `가입 동의는 시행 중인 약관 전부에 대해 만들고 제출하지 않은 약관은 미동의다`() {
        val agreements = Member.agreementsAtRegistration(mapOf("SERVICE" to true, "PRIVACY" to false), ACTIVE_TERMS, NOW)

        assertEquals(listOf("SERVICE", "PRIVACY", "MARKETING"), agreements.map { it.termsCode })
        assertEquals(listOf(11L, 12L, 13L), agreements.map { it.termsId })
        assertEquals(listOf("1.0", "2.0", "3.0"), agreements.map { it.termsVersion })
        assertEquals(listOf(true, false, false), agreements.map { it.agreed })
        assertEquals(listOf(NOW, NOW, NOW), agreements.map { it.agreedAt })
    }

    @Test
    fun `시행 중이 아닌 코드로 제출한 동의는 버린다`() {
        val agreements = Member.agreementsAtRegistration(mapOf("UNKNOWN" to true), ACTIVE_TERMS, NOW)

        assertEquals(listOf("SERVICE", "PRIVACY", "MARKETING"), agreements.map { it.termsCode })
        assertEquals(listOf(false, false, false), agreements.map { it.agreed })
    }

    @Test
    fun `저장하면 id만 얹힌다`() {
        val before = registerEmail()
        val saved = before.withId(42L)

        assertEquals(42L, saved.id)
        assertEquals(before, saved.copy(id = null))
    }

    @Test
    fun `저장되지 않은 회원으로는 자식을 만들지 못한다`() {
        val unsaved = member(id = null, provider = Provider.KAKAO)

        assertFailsWith<IllegalStateException> { unsaved.issueEmailVerification(MemberFixtures.CODE, MemberFixtures.POLICY, Instant.EPOCH) }
    }
}
