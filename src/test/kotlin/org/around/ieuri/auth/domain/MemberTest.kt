package org.around.ieuri.auth.domain

import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.vo.Agreement
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.PasswordHash
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Member.register 불변식과 이메일 검증 여부 판정 규칙 — 순수 도메인 단위테스트(Spring/DB 없음). */
class MemberTest {

    private val now: Instant = Instant.parse("2026-07-16T00:00:00Z")
    private val requiredCodes = setOf("SERVICE", "PRIVACY")

    private fun agreements(vararg agreed: Pair<String, Boolean>): List<Agreement> = agreed.mapIndexed { idx, (code, ok) -> Agreement((idx + 1).toLong(), code, "1.0", ok, now) }

    private fun register(
        fallbackEmail: String = "user@example.com",
        providerEmail: String? = "user@example.com",
        ageVerified: Boolean = true,
        agreements: List<Agreement> = agreements("SERVICE" to true, "PRIVACY" to true, "MARKETING" to false),
    ): Member = Member.register(
        provider = Provider.GOOGLE,
        providerUserId = ProviderUserId("google-sub-1"),
        providerEmail = providerEmail?.let { Email(it) },
        fallbackEmail = Email(fallbackEmail),
        nickname = Nickname("여행자"),
        profileImageUrl = null,
        ageVerified = ageVerified,
        agreements = agreements,
        requiredTermsCodes = requiredCodes,
        now = now,
    )

    @Test
    fun `필수 약관 모두 동의하고 만14세 이상이면 가입에 성공한다`() {
        val member = register()
        assertEquals(Provider.GOOGLE, member.provider)
        assertEquals("user@example.com", member.email.value)
        assertTrue(member.ageVerified)
    }

    @Test
    fun `만 14세 미만이면 가입이 거부된다`() {
        assertFailsWith<IllegalArgumentException> { register(ageVerified = false) }
    }

    @Test
    fun `필수 약관에 미동의하면 가입이 거부된다`() {
        assertFailsWith<IllegalArgumentException> {
            register(agreements = agreements("SERVICE" to true, "PRIVACY" to false))
        }
    }

    @Test
    fun `이메일이 비어 있으면 가입이 거부된다`() {
        assertFailsWith<IllegalArgumentException> { register(providerEmail = null, fallbackEmail = "") }
    }

    // --- 인증 면제 판정 (SPEC-0024 R2·R3·R12) ---

    @Test
    fun `provider가 이메일을 주면 email_verified가 true다`() {
        assertTrue(register(providerEmail = "same@example.com").emailVerified)
    }

    /** R2-2. provider의 검증 플래그를 보지 않는다 — provider 이메일이 있으면 인증이 면제된다(POL-030). */
    @Test
    fun `provider의 검증 플래그와 무관하게 이메일만 주면 email_verified가 true다`() {
        // 판정에 플래그가 쓰이지 않으므로 파라미터가 없다.
        assertTrue(register(providerEmail = "unverified-by-provider@example.com").emailVerified)
    }

    @Test
    fun `provider가 이메일을 주지 않으면 email_verified가 false다`() {
        val member = register(providerEmail = null, fallbackEmail = "typed@example.com")
        assertFalse(member.emailVerified)
        assertEquals("typed@example.com", member.email.value)
    }

    /** R12-1·R12-2 — 제출값과 provider 값이 갈리면 **provider 값이 저장된다.** */
    @Test
    fun `provider가 이메일을 주면 제출값을 무시하고 provider 값을 저장한다`() {
        val member = register(providerEmail = "from-provider@example.com", fallbackEmail = "typed-by-user@example.com")
        assertEquals("from-provider@example.com", member.email.value)
        assertTrue(member.emailVerified)
    }

    @Test
    fun `markEmailVerified 호출 시 검증 상태로 전이된다`() {
        val member = register(providerEmail = null)
        assertFalse(member.emailVerified)
        member.markEmailVerified()
        assertTrue(member.emailVerified)
    }

    // --- 이메일 변경 가능 여부 (SPEC-0024 R6) ---

    @Test
    fun `SNS 회원은 이메일을 변경할 수 없다`() {
        assertFalse(register().canChangeEmail())
    }

    @Test
    fun `EMAIL 회원은 이메일을 변경할 수 있다`() {
        assertTrue(registerByEmail().canChangeEmail())
    }

    // --- 인증 주소 재입력 (SPEC-0024 R7) ---

    /** 정정 자체는 도메인이 하지 않는다 — 통과 여부만 판정하고 주소는 챌린지가 들고 있는다. */
    @Test
    fun `미인증 회원은 인증 대상 주소를 정정할 수 있다`() {
        val member = register(providerEmail = null, fallbackEmail = "wrong@example.com")

        member.ensureCanRetargetPendingEmail()

        // 회원의 주소는 그대로다 — 소유를 증명하기 전에는 바뀌지 않는다.
        assertEquals("wrong@example.com", member.email.value)
        assertFalse(member.emailVerified)
    }

    @Test
    fun `인증을 마친 회원의 주소 정정은 거부된다`() {
        val member = register(providerEmail = "verified@example.com")
        assertTrue(member.emailVerified)

        assertFailsWith<IllegalArgumentException> { member.ensureCanRetargetPendingEmail() }
    }

    // --- registerByEmail (SPEC-0012 R1) ---

    private fun registerByEmail(
        email: String = "user@example.com",
        providerUserId: String = "protected-value",
    ): Member = Member.registerByEmail(
        email = Email(email),
        providerUserId = ProviderUserId(providerUserId),
        passwordHash = PasswordHash("hashed-pw"),
        nickname = Nickname("여행자"),
        ageVerified = true,
        agreements = agreements("SERVICE" to true, "PRIVACY" to true),
        requiredTermsCodes = requiredCodes,
        now = now,
    )

    @Test
    fun `이메일 가입은 넘겨받은 식별자를 그대로 쓰고 이메일에서 파생하지 않는다`() {
        val member = registerByEmail(email = "user@example.com", providerUserId = "protected-value")

        assertEquals(Provider.EMAIL, member.provider)
        assertEquals("protected-value", member.providerUserId.value)
        assertEquals("user@example.com", member.email.value)
        assertFalse(member.emailVerified)
    }

    @Test
    fun `이메일 가입도 필수 약관 미동의면 거부된다`() {
        assertFailsWith<IllegalArgumentException> {
            Member.registerByEmail(
                email = Email("user@example.com"),
                providerUserId = ProviderUserId("protected-value"),
                passwordHash = PasswordHash("hashed-pw"),
                nickname = Nickname("여행자"),
                ageVerified = true,
                agreements = agreements("SERVICE" to true),
                requiredTermsCodes = requiredCodes,
                now = now,
            )
        }
    }

    // --- 상태값 집합 (SPEC-0012 R12) ---

    @Test
    fun `회원 상태는 ACTIVE와 SUSPENDED 둘뿐이다`() {
        // 탈퇴는 행 삭제라 WITHDRAWN 같은 상태값이 존재해선 안 된다.
        assertEquals(
            listOf(MemberStatus.ACTIVE, MemberStatus.SUSPENDED),
            MemberStatus.entries.toList(),
        )
    }
}
