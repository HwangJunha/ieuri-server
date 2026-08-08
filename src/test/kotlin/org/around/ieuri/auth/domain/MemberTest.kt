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

    private fun agreements(vararg agreed: Pair<String, Boolean>): List<Agreement> =
        agreed.mapIndexed { idx, (code, ok) -> Agreement((idx + 1).toLong(), code, "1.0", ok, now) }

    private fun register(
        submittedEmail: String = "user@example.com",
        providerEmail: String? = "user@example.com",
        providerEmailVerified: Boolean = true,
        ageVerified: Boolean = true,
        agreements: List<Agreement> = agreements("SERVICE" to true, "PRIVACY" to true, "MARKETING" to false),
    ): Member = Member.register(
        provider = Provider.GOOGLE,
        providerUserId = ProviderUserId("google-sub-1"),
        submittedEmail = Email(submittedEmail),
        providerEmail = providerEmail?.let { Email(it) },
        providerEmailVerified = providerEmailVerified,
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
        assertFailsWith<IllegalArgumentException> { register(submittedEmail = "") }
    }

    @Test
    fun `제출 이메일이 provider 검증 이메일과 같으면 email_verified가 true다`() {
        val member = register(submittedEmail = "SAME@Example.com", providerEmail = "same@example.com", providerEmailVerified = true)
        assertTrue(member.emailVerified)
    }

    @Test
    fun `사용자가 이메일을 수정했으면 email_verified가 false로 시작한다`() {
        val member = register(submittedEmail = "edited@example.com", providerEmail = "original@example.com", providerEmailVerified = true)
        assertFalse(member.emailVerified)
    }

    @Test
    fun `provider가 이메일을 검증하지 않았으면 email_verified가 false다`() {
        val member = register(providerEmailVerified = false)
        assertFalse(member.emailVerified)
    }

    @Test
    fun `provider가 이메일을 주지 않았으면 email_verified가 false다`() {
        val member = register(submittedEmail = "typed@example.com", providerEmail = null, providerEmailVerified = false)
        assertFalse(member.emailVerified)
    }

    @Test
    fun `markEmailVerified 호출 시 검증 상태로 전이된다`() {
        val member = register(providerEmailVerified = false)
        assertFalse(member.emailVerified)
        member.markEmailVerified()
        assertTrue(member.emailVerified)
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
