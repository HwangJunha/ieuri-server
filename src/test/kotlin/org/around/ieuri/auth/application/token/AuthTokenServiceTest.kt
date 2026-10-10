package org.around.ieuri.auth.application.token

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.MemberLoginUseCase
import org.around.ieuri.auth.application.member.provided.exception.InvalidCredentialsException
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.token.provided.dto.LoginCommand
import org.around.ieuri.auth.application.token.provided.dto.LoginEmailCommand
import org.around.ieuri.auth.application.token.provided.dto.LogoutCommand
import org.around.ieuri.auth.application.token.provided.dto.RefreshTokenCommand
import org.around.ieuri.auth.application.token.provided.dto.TokenPairResult
import org.around.ieuri.auth.application.token.provided.exception.InvalidTokenException
import org.around.ieuri.auth.application.token.provided.exception.TokenReuseDetectedException
import org.around.ieuri.auth.application.token.required.RefreshTokenStorePort
import org.around.ieuri.auth.application.token.required.TokenEventPublisherPort
import org.around.ieuri.auth.application.token.required.TokenProviderPort
import org.around.ieuri.auth.application.token.required.dto.RefreshTokenClaims
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 로그인·refresh 회전·로그아웃.
 * - 회전은 새 토큰 발급이 옛 토큰 폐기보다 먼저다(SPEC-0032 R5).
 * - 이미 폐기된 refresh의 재제출은 탈취로 보고 회원의 세션을 모두 끊는다.
 */
class AuthTokenServiceTest {

    private val memberLogin = mockk<MemberLoginUseCase>()
    private val tokenProvider = mockk<TokenProviderPort>()
    private val refreshTokenStore = mockk<RefreshTokenStorePort>(relaxUnitFun = true)
    private val tokenIssuer = mockk<TokenIssuer>()
    private val eventPublisher = mockk<TokenEventPublisherPort>()
    private val events = mutableListOf<Any>()

    private val service = AuthTokenService(memberLogin, tokenProvider, refreshTokenStore, tokenIssuer, eventPublisher)

    private val tokens = TokenPairResult("acc", "ref", "Bearer", 300, 2_592_000)
    private val claims = RefreshTokenClaims(memberId = 100L, jti = "jti-1", expiresAt = Instant.parse("2026-08-16T00:00:00Z"))

    init {
        every { eventPublisher.publish(capture(events)) } just runs
    }

    // --- 로그인 ---

    @Test
    fun `SNS 로그인은 인증된 회원의 토큰을 발급하고 접속기록을 남긴다`() {
        every { memberLogin.loginBySns(Provider.GOOGLE, "id-token", "auth-code-1") } returns 100L
        every { tokenIssuer.issueFor(100L) } returns tokens

        val result = service.loginBySns(LoginCommand(Provider.GOOGLE, "id-token", authorizationCode = "auth-code-1", ipAddress = "203.0.113.7"))

        assertEquals(tokens, result)
        assertEquals(listOf<Any>(MemberAccessed(100L, AccessType.LOGIN, "203.0.113.7")), events)
    }

    @Test
    fun `SNS 인증이 거부되면 토큰을 발급하지 않고 접속기록도 남기지 않는다`() {
        every { memberLogin.loginBySns(any(), any(), any()) } throws MemberNotFoundException("가입되지 않은 계정입니다. 회원가입이 필요합니다.")

        assertFailsWith<MemberNotFoundException> { service.loginBySns(LoginCommand(Provider.GOOGLE, "id-token")) }

        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `이메일 로그인은 인증된 회원의 토큰을 발급하고 접속기록을 남긴다`() {
        every { memberLogin.loginByEmail("user@example.com", "Passw0rd!") } returns 300L
        every { tokenIssuer.issueFor(300L) } returns tokens

        val result = service.loginByEmail(LoginEmailCommand("user@example.com", "Passw0rd!", ipAddress = null))

        assertEquals(tokens, result)
        // 접속지를 알 수 없으면 null로 남긴다.
        assertEquals(listOf<Any>(MemberAccessed(300L, AccessType.LOGIN, null)), events)
    }

    @Test
    fun `이메일 인증이 거부되면 토큰을 발급하지 않고 접속기록도 남기지 않는다`() {
        every { memberLogin.loginByEmail(any(), any()) } throws InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.")

        assertFailsWith<InvalidCredentialsException> { service.loginByEmail(LoginEmailCommand("user@example.com", "wrong")) }

        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
        assertTrue(events.isEmpty())
    }

    // --- refresh 회전 ---

    /** 순서를 바꾸면 중간 실패 뒤의 재시도가 탈취로 오판되어 전 기기가 로그아웃된다. */
    @Test
    fun `살아 있는 refresh면 새 토큰을 발급한 뒤에 옛 토큰을 폐기한다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims
        every { refreshTokenStore.existsActive("jti-1") } returns true
        every { tokenIssuer.issueFor(100L) } returns tokens

        val result = service.refresh(RefreshTokenCommand("ref"))

        assertEquals(tokens, result)
        verifyOrder {
            refreshTokenStore.existsActive("jti-1")
            tokenIssuer.issueFor(100L)
            refreshTokenStore.revoke("jti-1")
        }
        verify(exactly = 0) { refreshTokenStore.revokeAllByMember(any()) }
    }

    @Test
    fun `발급이 실패하면 옛 토큰을 폐기하지 않는다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims
        every { refreshTokenStore.existsActive("jti-1") } returns true
        every { tokenIssuer.issueFor(100L) } throws IllegalStateException("발급 실패")

        assertFailsWith<IllegalStateException> { service.refresh(RefreshTokenCommand("ref")) }

        verify(exactly = 0) { refreshTokenStore.revoke(any()) }
    }

    @Test
    fun `이미 폐기된 refresh를 다시 내면 회원의 세션을 모두 끊고 재사용으로 거부한다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims
        every { refreshTokenStore.existsActive("jti-1") } returns false

        val ex = assertFailsWith<TokenReuseDetectedException> { service.refresh(RefreshTokenCommand("ref")) }

        assertEquals("TOKEN_REUSE_DETECTED", ex.code)
        assertEquals("재사용된 리프레시 토큰입니다. 다시 로그인해 주세요.", ex.message)
        verify(exactly = 1) { refreshTokenStore.revokeAllByMember(100L) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
        verify(exactly = 0) { refreshTokenStore.revoke(any()) }
    }

    @Test
    fun `서명이나 만료가 틀린 refresh는 저장소를 묻지 않고 그대로 거부한다`() {
        every { tokenProvider.parseRefreshToken("bad") } throws InvalidTokenException("invalid")

        assertFailsWith<InvalidTokenException> { service.refresh(RefreshTokenCommand("bad")) }

        verify(exactly = 0) { refreshTokenStore.existsActive(any()) }
        verify(exactly = 0) { refreshTokenStore.revokeAllByMember(any()) }
        verify(exactly = 0) { tokenIssuer.issueFor(any()) }
    }

    // --- 로그아웃 ---

    @Test
    fun `로그아웃은 제출된 refresh만 폐기하고 접속기록을 남긴다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims

        val result = service.logout(LogoutCommand("ref", ipAddress = "203.0.113.7"))

        assertTrue(result.success)
        verify(exactly = 1) { refreshTokenStore.revoke("jti-1") }
        verify(exactly = 0) { refreshTokenStore.revokeAllByMember(any()) }
        assertEquals(listOf<Any>(MemberAccessed(100L, AccessType.LOGOUT, "203.0.113.7")), events)
    }

    /** 멱등 — 이미 무효·만료된 토큰이어도 성공이다. 누구의 로그아웃인지 알 수 없어 기록도 없다. */
    @Test
    fun `무효한 토큰의 로그아웃은 폐기도 기록도 없이 성공한다`() {
        every { tokenProvider.parseRefreshToken("bad") } throws InvalidTokenException("invalid")

        val result = service.logout(LogoutCommand("bad"))

        assertTrue(result.success)
        verify(exactly = 0) { refreshTokenStore.revoke(any()) }
        assertTrue(events.isEmpty())
    }
}
