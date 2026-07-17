package org.around.ieuri.auth.infrastructure

import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.infrastructure.adapter.out.token.JjwtTokenProvider
import org.around.ieuri.auth.infrastructure.config.JwtProperties
import java.time.Clock
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** JjwtTokenProvider — 발급/검증 라운드트립과 타입/서명 검증(순수, Spring 없음). */
class JjwtTokenProviderTest {

    private val properties = JwtProperties(
        issuer = "ieuri-test",
        secret = "aWV1cmlfbG9jYWxfZGV2X2p3dF9oczI1Nl9zZWNyZXQ=", // base64 32바이트
        accessTtl = Duration.ofMinutes(5),
        refreshTtl = Duration.ofDays(30),
    )
    private val provider = JjwtTokenProvider(properties, Clock.systemUTC())

    @Test
    fun `refresh 토큰 발급 후 파싱하면 memberId와 jti가 일치한다`() {
        val issued = provider.issueRefreshToken(100L)
        val claims = provider.parseRefreshToken(issued.token)

        assertEquals(100L, claims.memberId)
        assertEquals(issued.jti, claims.jti)
        assertTrue(claims.expiresAt.isAfter(java.time.Instant.now()))
    }

    @Test
    fun `access 토큰 발급 후 파싱하면 memberId가 일치한다`() {
        val issued = provider.issueAccessToken(100L)
        assertEquals(100L, provider.parseAccessToken(issued.token).memberId)
    }

    @Test
    fun `access 토큰을 refresh 자리에 넣으면 타입 불일치로 거부된다`() {
        val access = provider.issueAccessToken(100L)
        assertFailsWith<InvalidTokenException> { provider.parseRefreshToken(access.token) }
    }

    @Test
    fun `refresh 토큰을 access 자리에 넣으면 타입 불일치로 거부된다`() {
        val refresh = provider.issueRefreshToken(100L)
        assertFailsWith<InvalidTokenException> { provider.parseAccessToken(refresh.token) }
    }

    @Test
    fun `변조되었거나 형식이 잘못된 토큰은 거부된다`() {
        assertFailsWith<InvalidTokenException> { provider.parseRefreshToken("not-a-jwt") }

        val issued = provider.issueRefreshToken(100L)
        val tampered = issued.token.dropLast(2) + "xx"
        assertFailsWith<InvalidTokenException> { provider.parseRefreshToken(tampered) }
    }
}
