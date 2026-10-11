package org.around.ieuri.auth.application.token

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.auth.application.token.provided.dto.AccessTokenClaims
import org.around.ieuri.auth.application.token.provided.exception.InvalidTokenException
import org.around.ieuri.auth.application.token.required.RefreshTokenStorePort
import org.around.ieuri.auth.application.token.required.TokenProviderPort
import org.around.ieuri.auth.domain.token.spec.LiveSubscriptionIdentity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthTokenQueryServiceTest {

    private val now = Instant.parse("2026-08-25T00:04:00Z")
    private val tokenProvider = mockk<TokenProviderPort>()
    private val refreshTokenStore = mockk<RefreshTokenStorePort>()
    private val service = AuthTokenQueryService(tokenProvider, refreshTokenStore, LiveSubscriptionIdentity(Duration.ofMinutes(5)), Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `accessToken의 클레임을 그대로 돌려주고 무효 토큰의 예외는 삼키지 않는다`() {
        val claims = mockk<AccessTokenClaims>()
        every { tokenProvider.parseAccessToken("valid") } returns claims
        every { tokenProvider.parseAccessToken("bad") } throws InvalidTokenException("무효")

        assertEquals(claims, service.authenticate("valid"))
        assertFailsWith<InvalidTokenException> { service.authenticate("bad") }
    }

    @Test
    fun `토큰이 살아 있고 연결이 TTL 안이면 구독을 열 수 있다`() {
        every { refreshTokenStore.hasActiveToken(7) } returns true

        assertTrue(service.canOpenSubscription(7, connectedAt = now.minusSeconds(60)))
        assertFalse(service.canOpenSubscription(7, connectedAt = now.minusSeconds(301)))
    }

    @Test
    fun `토큰이 전부 폐기됐으면 구독을 열 수 없다`() {
        every { refreshTokenStore.hasActiveToken(7) } returns false

        assertFalse(service.canOpenSubscription(7, connectedAt = now.minusSeconds(1)))
    }
}
