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
    fun `access 토큰 발급 후 파싱하면 memberId와 권한·역할이 일치한다`() {
        val issued = provider.issueAccessToken(100L, setOf("member:read", "member:suspend"), setOf("ADMIN"))
        val claims = provider.parseAccessToken(issued.token)

        assertEquals(100L, claims.memberId)
        assertEquals(setOf("member:read", "member:suspend"), claims.permissions)
        assertEquals(setOf("ADMIN"), claims.roles)
    }

    @Test
    fun `권한이 없는 access 토큰은 빈 권한 집합으로 파싱된다`() {
        val issued = provider.issueAccessToken(100L, emptySet(), setOf("USER"))
        val claims = provider.parseAccessToken(issued.token)

        assertEquals(100L, claims.memberId)
        assertTrue(claims.permissions.isEmpty())
        assertEquals(setOf("USER"), claims.roles)
    }

    @Test
    fun `access 토큰을 refresh 자리에 넣으면 타입 불일치로 거부된다`() {
        val access = provider.issueAccessToken(100L, setOf("member:read"), setOf("ADMIN"))
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

        // **페이로드**의 한 글자를 바꾼다. 서명 끝 글자를 건드리면 간헐적으로 통과한다 —
        // base64url의 마지막 문자는 남는 비트만 싣기 때문에, 서로 다른 문자가 같은 바이트열로
        // 디코딩되어 서명이 그대로 유효해지는 경우가 있다.
        val issued = provider.issueRefreshToken(100L)
        val (header, payload, signature) = issued.token.split('.')
        val flipped = payload.first().let { if (it == 'e') 'f' else 'e' } + payload.drop(1)
        assertFailsWith<InvalidTokenException> {
            provider.parseRefreshToken("$header.$flipped.$signature")
        }
    }
}
