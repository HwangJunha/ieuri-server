package org.around.ieuri.auth.application.token

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.auth.application.member.provided.MemberQueryUseCase
import org.around.ieuri.auth.application.member.provided.dto.MemberAuthoritiesView
import org.around.ieuri.auth.application.token.required.RefreshTokenStorePort
import org.around.ieuri.auth.application.token.required.TokenProviderPort
import org.around.ieuri.auth.application.token.required.dto.IssuedRefreshToken
import org.around.ieuri.auth.application.token.required.dto.IssuedToken
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 토큰 쌍 발급. 발급마다 유효 권한을 다시 읽고, refresh의 jti를 저장소에 등록한다. */
class TokenIssuerTest {

    private val now = Instant.parse("2026-07-17T00:00:00Z")
    private val refreshExpiresAt = now.plusSeconds(2_592_000)

    private val tokenProvider = mockk<TokenProviderPort>()
    private val refreshTokenStore = mockk<RefreshTokenStorePort>(relaxUnitFun = true)
    private val memberQuery = mockk<MemberQueryUseCase>()
    private val issuer = TokenIssuer(tokenProvider, refreshTokenStore, memberQuery, Clock.fixed(now, ZoneOffset.UTC))

    private fun givenIssuable() {
        every { memberQuery.authoritiesOf(100L) } returns MemberAuthoritiesView(roles = setOf("ADMIN"), permissions = setOf("member:read"))
        every { tokenProvider.issueAccessToken(100L, permissions = setOf("member:read"), roles = setOf("ADMIN")) } returns IssuedToken("acc", now.plusSeconds(300))
        every { tokenProvider.issueRefreshToken(100L) } returns IssuedRefreshToken("ref", "jti-1", refreshExpiresAt)
    }

    /** 역할과 권한은 둘 다 문자열 집합이라 뒤바뀌어도 컴파일된다 — 서로 다른 값으로 고정한다. */
    @Test
    fun `회원의 권한과 역할을 access 토큰에 싣고 남은 유효기간을 초로 돌려준다`() {
        givenIssuable()

        val result = issuer.issueFor(100L)

        assertEquals("acc", result.accessToken)
        assertEquals("ref", result.refreshToken)
        assertEquals("Bearer", result.tokenType)
        assertEquals(300L, result.accessTokenExpiresInSeconds)
        assertEquals(2_592_000L, result.refreshTokenExpiresInSeconds)
        verify(exactly = 1) { tokenProvider.issueAccessToken(100L, permissions = setOf("member:read"), roles = setOf("ADMIN")) }
    }

    @Test
    fun `발급한 refresh의 jti를 회원과 만료 시각과 함께 저장소에 등록한다`() {
        givenIssuable()

        issuer.issueFor(100L)

        verifyOrder {
            tokenProvider.issueRefreshToken(100L)
            refreshTokenStore.save("jti-1", 100L, refreshExpiresAt)
        }
    }

    /** 저장소가 fail-closed로 던지면 등록되지 않은 토큰을 돌려주지 않는다. */
    @Test
    fun `jti 등록이 실패하면 예외를 그대로 내보낸다`() {
        givenIssuable()
        every { refreshTokenStore.save(any(), any(), any()) } throws IllegalStateException("저장소 장애")

        assertFailsWith<IllegalStateException> { issuer.issueFor(100L) }
    }
}
