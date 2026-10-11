package org.around.ieuri.auth.application.token

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.MemberLoginUseCase
import org.around.ieuri.auth.application.member.provided.MemberQueryUseCase
import org.around.ieuri.auth.application.member.provided.dto.MemberAuthoritiesView
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
import org.around.ieuri.auth.application.token.required.dto.IssuedRefreshToken
import org.around.ieuri.auth.application.token.required.dto.IssuedToken
import org.around.ieuri.auth.application.token.required.dto.RefreshTokenClaims
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 로그인·refresh 회전·로그아웃과 토큰 쌍 발급.
 * - 회전은 새 토큰 발급이 옛 토큰 폐기보다 먼저다(SPEC-0032 R5).
 * - 이미 폐기된 refresh의 재제출은 탈취로 보고 회원의 세션을 모두 끊는다.
 * - 발급마다 유효 권한을 다시 읽고, refresh의 jti를 저장소에 등록한다.
 */
class AuthTokenServiceTest {

    private val now = Instant.parse("2026-07-17T00:00:00Z")
    private val refreshExpiresAt = now.plusSeconds(2_592_000)

    private val memberLogin = mockk<MemberLoginUseCase>()
    private val memberQuery = mockk<MemberQueryUseCase>()
    private val tokenProvider = mockk<TokenProviderPort>()
    private val refreshTokenStore = mockk<RefreshTokenStorePort>(relaxUnitFun = true)
    private val eventPublisher = mockk<TokenEventPublisherPort>()
    private val events = mutableListOf<Any>()

    private val service = AuthTokenService(memberLogin, memberQuery, tokenProvider, refreshTokenStore, eventPublisher, Clock.fixed(now, ZoneOffset.UTC))

    private val tokens = TokenPairResult("acc", "ref", "Bearer", 300, 2_592_000)

    // 제출된(옛) refresh의 클레임. 새로 발급되는 jti(`jti-new`)와 구분한다.
    private val claims = RefreshTokenClaims(memberId = 100L, jti = "jti-1", expiresAt = Instant.parse("2026-08-16T00:00:00Z"))

    init {
        every { eventPublisher.publish(capture(events)) } just runs
    }

    /** 역할과 권한은 둘 다 문자열 집합이라 뒤바뀌어도 컴파일된다 — 서로 다른 값으로 고정한다. */
    private fun givenIssuable(memberId: Long) {
        every { memberQuery.authoritiesOf(memberId) } returns MemberAuthoritiesView(roles = setOf("ADMIN"), permissions = setOf("member:read"))
        every { tokenProvider.issueAccessToken(memberId, permissions = setOf("member:read"), roles = setOf("ADMIN")) } returns IssuedToken("acc", now.plusSeconds(300))
        every { tokenProvider.issueRefreshToken(memberId) } returns IssuedRefreshToken("ref", "jti-new", refreshExpiresAt)
    }

    private fun verifyNothingIssued() {
        verify(exactly = 0) { memberQuery.authoritiesOf(any()) }
        verify(exactly = 0) { tokenProvider.issueAccessToken(any(), any(), any()) }
        verify(exactly = 0) { tokenProvider.issueRefreshToken(any()) }
        verify(exactly = 0) { refreshTokenStore.save(any(), any(), any()) }
    }

    // --- 로그인 ---

    /** 접속지는 Command가 아니라 인자로 받는다 — 접속기록에는 그 값이 남는다. */
    @Test
    fun `SNS 로그인은 인증된 회원의 토큰을 발급하고 접속기록을 남긴다`() {
        every { memberLogin.loginBySns(Provider.GOOGLE, "id-token", "auth-code-1") } returns 100L
        givenIssuable(100L)

        val result = service.loginBySns(LoginCommand(Provider.GOOGLE, "id-token", authorizationCode = "auth-code-1"), ipAddress = "203.0.113.7")

        assertEquals(tokens, result)
        assertEquals(listOf<Any>(MemberAccessed(100L, AccessType.LOGIN, "203.0.113.7")), events)
    }

    @Test
    fun `SNS 인증이 거부되면 토큰을 발급하지 않고 접속기록도 남기지 않는다`() {
        every { memberLogin.loginBySns(any(), any(), any()) } throws MemberNotFoundException("가입되지 않은 계정입니다. 회원가입이 필요합니다.")

        assertFailsWith<MemberNotFoundException> { service.loginBySns(LoginCommand(Provider.GOOGLE, "id-token"), ipAddress = "203.0.113.7") }

        verifyNothingIssued()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `이메일 로그인은 인증된 회원의 토큰을 발급하고 접속기록을 남긴다`() {
        every { memberLogin.loginByEmail("user@example.com", "Passw0rd!") } returns 300L
        givenIssuable(300L)

        val result = service.loginByEmail(LoginEmailCommand("user@example.com", "Passw0rd!"), ipAddress = null)

        assertEquals(tokens, result)
        // 접속지를 알 수 없으면 null로 남긴다.
        assertEquals(listOf<Any>(MemberAccessed(300L, AccessType.LOGIN, null)), events)
    }

    @Test
    fun `이메일 로그인의 접속기록에는 인자로 받은 접속지가 남는다`() {
        every { memberLogin.loginByEmail("user@example.com", "Passw0rd!") } returns 300L
        givenIssuable(300L)

        service.loginByEmail(LoginEmailCommand("user@example.com", "Passw0rd!"), ipAddress = "198.51.100.9")

        assertEquals(listOf<Any>(MemberAccessed(300L, AccessType.LOGIN, "198.51.100.9")), events)
    }

    @Test
    fun `이메일 인증이 거부되면 토큰을 발급하지 않고 접속기록도 남기지 않는다`() {
        every { memberLogin.loginByEmail(any(), any()) } throws InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.")

        assertFailsWith<InvalidCredentialsException> { service.loginByEmail(LoginEmailCommand("user@example.com", "wrong"), ipAddress = "203.0.113.7") }

        verifyNothingIssued()
        assertTrue(events.isEmpty())
    }

    // --- 토큰 쌍 발급 ---

    @Test
    fun `회원의 권한과 역할을 access 토큰에 싣고 남은 유효기간을 초로 돌려준다`() {
        every { memberLogin.loginByEmail(any(), any()) } returns 100L
        givenIssuable(100L)

        val result = service.loginByEmail(LoginEmailCommand("user@example.com", "Passw0rd!"), ipAddress = null)

        assertEquals("acc", result.accessToken)
        assertEquals("ref", result.refreshToken)
        assertEquals("Bearer", result.tokenType)
        // 만료 시각에서 지금(Clock)을 뺀 값이다.
        assertEquals(300L, result.accessTokenExpiresInSeconds)
        assertEquals(2_592_000L, result.refreshTokenExpiresInSeconds)
        verify(exactly = 1) { tokenProvider.issueAccessToken(100L, permissions = setOf("member:read"), roles = setOf("ADMIN")) }
    }

    @Test
    fun `발급한 refresh의 jti를 회원과 만료 시각과 함께 저장소에 등록한다`() {
        every { memberLogin.loginByEmail(any(), any()) } returns 100L
        givenIssuable(100L)

        service.loginByEmail(LoginEmailCommand("user@example.com", "Passw0rd!"), ipAddress = null)

        verifyOrder {
            tokenProvider.issueRefreshToken(100L)
            refreshTokenStore.save("jti-new", 100L, refreshExpiresAt)
        }
    }

    /** 로그인 때 읽은 권한을 들고 있지 않는다 — 그 사이 역할이 바뀌면 재발급에 반영돼야 한다. */
    @Test
    fun `발급마다 유효 권한을 다시 읽는다`() {
        every { memberLogin.loginByEmail(any(), any()) } returns 100L
        givenIssuable(100L)
        every { tokenProvider.parseRefreshToken("ref") } returns claims
        every { refreshTokenStore.existsActive("jti-1") } returns true

        service.loginByEmail(LoginEmailCommand("user@example.com", "Passw0rd!"), ipAddress = null)
        every { memberQuery.authoritiesOf(100L) } returns MemberAuthoritiesView(roles = setOf("USER"), permissions = emptySet())
        every { tokenProvider.issueAccessToken(100L, permissions = emptySet(), roles = setOf("USER")) } returns IssuedToken("acc-2", now.plusSeconds(300))
        val refreshed = service.refresh(RefreshTokenCommand("ref"))

        assertEquals("acc-2", refreshed.accessToken)
        verify(exactly = 2) { memberQuery.authoritiesOf(100L) }
        verify(exactly = 1) { tokenProvider.issueAccessToken(100L, permissions = emptySet(), roles = setOf("USER")) }
    }

    /** 저장소가 fail-closed로 던지면 등록되지 않은 토큰을 돌려주지 않는다. */
    @Test
    fun `jti 등록이 실패하면 예외를 그대로 내보낸다`() {
        every { memberLogin.loginByEmail(any(), any()) } returns 100L
        givenIssuable(100L)
        every { refreshTokenStore.save(any(), any(), any()) } throws IllegalStateException("저장소 장애")

        assertFailsWith<IllegalStateException> { service.loginByEmail(LoginEmailCommand("user@example.com", "Passw0rd!"), ipAddress = null) }
    }

    // --- refresh 회전 ---

    /** 순서를 바꾸면 중간 실패 뒤의 재시도가 탈취로 오판되어 전 기기가 로그아웃된다. */
    @Test
    fun `살아 있는 refresh면 새 토큰을 발급한 뒤에 옛 토큰을 폐기한다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims
        every { refreshTokenStore.existsActive("jti-1") } returns true
        givenIssuable(100L)

        val result = service.refresh(RefreshTokenCommand("ref"))

        assertEquals(tokens, result)
        verifyOrder {
            refreshTokenStore.existsActive("jti-1")
            refreshTokenStore.save("jti-new", 100L, refreshExpiresAt)
            refreshTokenStore.revoke("jti-1")
        }
        verify(exactly = 0) { refreshTokenStore.revokeAllByMember(any()) }
        // 회전은 접속기록 대상이 아니다.
        assertTrue(events.isEmpty())
    }

    @Test
    fun `발급이 실패하면 옛 토큰을 폐기하지 않는다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims
        every { refreshTokenStore.existsActive("jti-1") } returns true
        givenIssuable(100L)
        every { tokenProvider.issueAccessToken(any(), any(), any()) } throws IllegalStateException("발급 실패")

        assertFailsWith<IllegalStateException> { service.refresh(RefreshTokenCommand("ref")) }

        verify(exactly = 0) { refreshTokenStore.revoke(any()) }
    }

    /** 새 jti가 등록되지 않았는데 옛 것을 폐기하면 회원이 세션을 잃는다. */
    @Test
    fun `새 jti 등록이 실패하면 옛 토큰을 폐기하지 않는다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims
        every { refreshTokenStore.existsActive("jti-1") } returns true
        givenIssuable(100L)
        every { refreshTokenStore.save(any(), any(), any()) } throws IllegalStateException("저장소 장애")

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
        verifyNothingIssued()
        verify(exactly = 0) { refreshTokenStore.revoke(any()) }
    }

    @Test
    fun `서명이나 만료가 틀린 refresh는 저장소를 묻지 않고 그대로 거부한다`() {
        every { tokenProvider.parseRefreshToken("bad") } throws InvalidTokenException("invalid")

        assertFailsWith<InvalidTokenException> { service.refresh(RefreshTokenCommand("bad")) }

        verify(exactly = 0) { refreshTokenStore.existsActive(any()) }
        verify(exactly = 0) { refreshTokenStore.revokeAllByMember(any()) }
        verifyNothingIssued()
    }

    // --- 로그아웃 ---

    @Test
    fun `로그아웃은 제출된 refresh만 폐기하고 접속기록을 남긴다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims

        val result = service.logout(LogoutCommand("ref"), ipAddress = "203.0.113.7")

        assertTrue(result.success)
        verify(exactly = 1) { refreshTokenStore.revoke("jti-1") }
        verify(exactly = 0) { refreshTokenStore.revokeAllByMember(any()) }
        assertEquals(listOf<Any>(MemberAccessed(100L, AccessType.LOGOUT, "203.0.113.7")), events)
    }

    @Test
    fun `접속지를 알 수 없는 로그아웃은 접속기록에 null을 남긴다`() {
        every { tokenProvider.parseRefreshToken("ref") } returns claims

        service.logout(LogoutCommand("ref"), ipAddress = null)

        assertEquals(listOf<Any>(MemberAccessed(100L, AccessType.LOGOUT, null)), events)
    }

    /** 멱등 — 이미 무효·만료된 토큰이어도 성공이다. 누구의 로그아웃인지 알 수 없어 기록도 없다. */
    @Test
    fun `무효한 토큰의 로그아웃은 폐기도 기록도 없이 성공한다`() {
        every { tokenProvider.parseRefreshToken("bad") } throws InvalidTokenException("invalid")

        val result = service.logout(LogoutCommand("bad"), ipAddress = "203.0.113.7")

        assertTrue(result.success)
        verify(exactly = 0) { refreshTokenStore.revoke(any()) }
        assertTrue(events.isEmpty())
    }
}
