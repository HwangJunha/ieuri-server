package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.application.member.provided.dto.EmailCredentialView
import org.around.ieuri.auth.application.member.provided.exception.InvalidCredentialsException
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.provided.exception.SnsVerificationFailedException
import org.around.ieuri.auth.application.member.required.PasswordEncoderPort
import org.around.ieuri.auth.application.member.required.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.member.required.dto.SnsIdentity
import org.around.ieuri.auth.domain.member.InactiveMemberException
import org.around.ieuri.auth.domain.member.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 회원 인증. 외부 호출(id_token 검증·토큰 교환·비밀번호 대조)을 DB 단계 밖에 둔다.
 * - 이메일 로그인의 실패는 사유를 나누지 않는다 — 계정 존재 여부가 드러나면 안 된다.
 */
class MemberLoginServiceTest {

    private val snsVerifier = mockk<SnsIdentityVerifierPort>()
    private val members = mockk<MemberCommandUseCase>(relaxUnitFun = true)
    private val providerTokenCollector = mockk<ProviderTokenCollector>(relaxUnitFun = true)
    private val passwordEncoder = mockk<PasswordEncoderPort>()

    private val service = MemberLoginService(snsVerifier, members, providerTokenCollector, passwordEncoder, prefixingIdentifierProtector())

    private val identity = SnsIdentity("google-sub-1", "u@example.com", "여행자", null)

    // --- SNS ---

    @Test
    fun `SNS 로그인은 검증 → 회원 인증 → provider 토큰 보충 순으로 하고 회원 id를 돌려준다`() {
        every { snsVerifier.verify(Provider.GOOGLE, "id-token") } returns identity
        every { members.admitSns(Provider.GOOGLE, "google-sub-1") } returns 100L

        val memberId = service.loginBySns(Provider.GOOGLE, "id-token", "auth-code-1")

        assertEquals(100L, memberId)
        verifyOrder {
            snsVerifier.verify(Provider.GOOGLE, "id-token")
            members.admitSns(Provider.GOOGLE, "google-sub-1")
            providerTokenCollector.collect(100L, Provider.GOOGLE, "auth-code-1")
        }
    }

    @Test
    fun `id_token 검증이 실패하면 회원을 조회하지 않는다`() {
        every { snsVerifier.verify(any(), any()) } throws SnsVerificationFailedException("SNS 인증에 실패했습니다.")

        assertFailsWith<SnsVerificationFailedException> { service.loginBySns(Provider.GOOGLE, "id-token", "auth-code-1") }

        verify(exactly = 0) { members.admitSns(any(), any()) }
        verify(exactly = 0) { providerTokenCollector.collect(any(), any(), any()) }
    }

    @Test
    fun `미가입이거나 정지된 회원이면 provider 토큰을 보충하지 않는다`() {
        every { snsVerifier.verify(any(), any()) } returns identity
        every { members.admitSns(any(), any()) } throws MemberNotFoundException("가입되지 않은 계정입니다. 회원가입이 필요합니다.")

        val ex = assertFailsWith<MemberNotFoundException> { service.loginBySns(Provider.GOOGLE, "id-token", "auth-code-1") }

        assertEquals("가입되지 않은 계정입니다. 회원가입이 필요합니다.", ex.message)
        verify(exactly = 0) { providerTokenCollector.collect(any(), any(), any()) }
    }

    // --- 이메일 ---

    private val invalidCredentials = "이메일 또는 비밀번호가 올바르지 않습니다."

    /** 정규화(trim+lowercase) → 보호 변환 순서라 표기가 달라도 같은 키로 찾는다. */
    @Test
    fun `이메일 로그인은 보호된 식별자로 자격을 찾고 비밀번호가 맞으면 회원을 인증한다`() {
        every { members.findEmailCredential("protected:user@example.com") } returns EmailCredentialView(300L, "hashed-pw")
        every { passwordEncoder.matches("Passw0rd!", "hashed-pw") } returns true

        val memberId = service.loginByEmail("  USER@Example.COM ", "Passw0rd!")

        assertEquals(300L, memberId)
        verifyOrder {
            members.findEmailCredential("protected:user@example.com")
            passwordEncoder.matches("Passw0rd!", "hashed-pw")
            members.admit(300L)
        }
    }

    @Test
    fun `없는 계정은 비밀번호를 대조하지 않고 자격 오류로 답한다`() {
        every { members.findEmailCredential(any()) } returns null

        val ex = assertFailsWith<InvalidCredentialsException> { service.loginByEmail("ghost@example.com", "Passw0rd!") }

        assertEquals(invalidCredentials, ex.message)
        verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
        verify(exactly = 0) { members.admit(any()) }
    }

    /** 해시가 없으면 이메일 로그인이 안 되는 계정이다 — 그 사실도 드러내지 않는다. */
    @Test
    fun `비밀번호가 없는 계정은 없는 계정과 같은 자격 오류로 답한다`() {
        every { members.findEmailCredential(any()) } returns EmailCredentialView(300L, passwordHash = null)

        val ex = assertFailsWith<InvalidCredentialsException> { service.loginByEmail("sns@example.com", "Passw0rd!") }

        assertEquals(invalidCredentials, ex.message)
        verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
        verify(exactly = 0) { members.admit(any()) }
    }

    @Test
    fun `비밀번호가 틀리면 같은 자격 오류로 답하고 회원을 인증하지 않는다`() {
        every { members.findEmailCredential(any()) } returns EmailCredentialView(300L, "hashed-pw")
        every { passwordEncoder.matches("wrong", "hashed-pw") } returns false

        val ex = assertFailsWith<InvalidCredentialsException> { service.loginByEmail("user@example.com", "wrong") }

        assertEquals("INVALID_CREDENTIALS", ex.code)
        assertEquals(invalidCredentials, ex.message)
        verify(exactly = 0) { members.admit(any()) }
    }

    /** 비밀번호가 맞은 뒤의 정지 판정은 그대로 나간다. */
    @Test
    fun `비밀번호가 맞아도 정지된 회원이면 정지 오류가 나간다`() {
        every { members.findEmailCredential(any()) } returns EmailCredentialView(300L, "hashed-pw")
        every { passwordEncoder.matches(any(), any()) } returns true
        every { members.admit(300L) } throws InactiveMemberException("정지된 계정입니다.")

        val ex = assertFailsWith<InactiveMemberException> { service.loginByEmail("user@example.com", "Passw0rd!") }

        assertEquals("정지된 계정입니다.", ex.message)
    }
}
