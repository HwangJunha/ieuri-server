package org.around.ieuri.auth.infrastructure

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.application.port.out.token.AccessTokenClaims
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import org.around.ieuri.auth.infrastructure.security.GraphqlWebSocketAuthInterceptor
import org.around.ieuri.common.security.UnauthorizedException
import org.springframework.graphql.server.WebSocketSessionInfo
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * WebSocket 연결 수립 인증(SPEC-0017 R10, POL-048).
 *
 * **SecurityContext 정리가 이 테스트의 핵심이다.** WebSocket 프레임은 서블릿 필터 체인을 타지
 * 않아 `SecurityContextHolder`를 지워 주는 주체가 없고, 처리 스레드는 요청과 연결을 가로질러
 * 재사용된다. 남겨 두면 인증 없는 다음 요청이 앞 회원의 신원을 물려받아 **그의 대화를 조회하게
 * 된다** — 인증 실패가 아니라 잘못된 성공이라 조용하다.
 */
class GraphqlWebSocketAuthInterceptorTest {

    private val memberId = 7L
    private val tokenProvider = mockk<TokenProviderPort>()
    private val interceptor = GraphqlWebSocketAuthInterceptor(
        tokenProvider,
        Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC),
    )

    @AfterTest
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun sessionInfo(
        attributes: MutableMap<String, Any> = mutableMapOf(),
        headers: HttpHeaders = HttpHeaders(),
    ) = mockk<WebSocketSessionInfo> {
        every { this@mockk.attributes } returns attributes
        every { this@mockk.headers } returns headers
    }

    private fun headersWith(authorization: String) = HttpHeaders().apply { set("Authorization", authorization) }

    private fun givenValidToken() {
        every { tokenProvider.parseAccessToken("valid") } returns
            AccessTokenClaims(
                memberId = memberId,
                permissions = setOf("POST_WRITE"),
                roles = setOf("MEMBER"),
            )
    }

    @Test
    fun `유효한 토큰이면 세션 속성에 회원이 담긴다`() {
        givenValidToken()
        val attributes = mutableMapOf<String, Any>()

        interceptor.handleConnectionInitialization(
            sessionInfo(attributes),
            mapOf("Authorization" to "Bearer valid"),
        ).block()

        assertEquals(memberId, attributes["ieuri.memberId"])
    }

    /** 토큰이 없으면 **연결 자체를 거부한다** — 구독 시점까지 미루면 클라이언트가 성공했다고 믿는다. */
    @Test
    fun `토큰이 어디에도 없으면 연결이 거부된다`() {
        assertFailsWith<UnauthorizedException> {
            interceptor.handleConnectionInitialization(sessionInfo(), emptyMap()).block()
        }
    }

    /**
     * **핸드셰이크 헤더로 온 토큰도 받는다.** 브라우저는 커스텀 헤더를 못 붙여 페이로드를
     * 쓰지만, 네이티브 클라이언트와 API 도구는 헤더를 쓰는 쪽이 흔하다 — 한쪽만 받으면
     * 나머지 절반이 "토큰을 넣었는데 인증이 안 된다"는 상태에 빠진다.
     */
    @Test
    fun `헤더로 온 토큰도 인증된다`() {
        givenValidToken()
        val attributes = mutableMapOf<String, Any>()

        interceptor.handleConnectionInitialization(
            sessionInfo(attributes, headersWith("Bearer valid")),
            emptyMap(),
        ).block()

        assertEquals(memberId, attributes["ieuri.memberId"])
    }

    /** 페이로드가 헤더보다 우선한다 — 명시적으로 실은 쪽을 존중한다. */
    @Test
    fun `페이로드와 헤더가 모두 있으면 페이로드를 쓴다`() {
        givenValidToken()
        every { tokenProvider.parseAccessToken("other") } throws InvalidTokenException("무효")
        val attributes = mutableMapOf<String, Any>()

        interceptor.handleConnectionInitialization(
            sessionInfo(attributes, headersWith("Bearer other")),
            mapOf("Authorization" to "Bearer valid"),
        ).block()

        assertEquals(memberId, attributes["ieuri.memberId"])
    }

    /** 소문자 키를 쓰는 클라이언트가 있다. */
    @Test
    fun `소문자 authorization 키도 받는다`() {
        givenValidToken()
        val attributes = mutableMapOf<String, Any>()

        interceptor.handleConnectionInitialization(
            sessionInfo(attributes),
            mapOf("authorization" to "Bearer valid"),
        ).block()

        assertEquals(memberId, attributes["ieuri.memberId"])
    }

    @Test
    fun `Bearer 접두어가 없으면 연결이 거부된다`() {
        assertFailsWith<UnauthorizedException> {
            interceptor.handleConnectionInitialization(
                sessionInfo(),
                mapOf("Authorization" to "valid"),
            ).block()
        }
    }

    /** 접두어만 있고 토큰이 비면 받지 않는다 — 빈 문자열을 파싱하러 보내지 않는다. */
    @Test
    fun `Bearer 뒤가 비면 연결이 거부된다`() {
        assertFailsWith<UnauthorizedException> {
            interceptor.handleConnectionInitialization(
                sessionInfo(),
                mapOf("Authorization" to "Bearer   "),
            ).block()
        }
    }

    @Test
    fun `무효한 토큰이면 연결이 거부된다`() {
        every { tokenProvider.parseAccessToken("bad") } throws InvalidTokenException("무효")

        assertFailsWith<UnauthorizedException> {
            interceptor.handleConnectionInitialization(
                sessionInfo(),
                mapOf("Authorization" to "Bearer bad"),
            ).block()
        }
    }

    /**
     * 연결 수립은 SecurityContext를 세우지 않는다 — 세우는 것은 요청 처리 시점(`intercept`)이고,
     * 거기서만 세웠다 지운다. 수립 단계에서 세우면 지울 지점이 없어진다.
     */
    @Test
    fun `연결 수립은 SecurityContext를 건드리지 않는다`() {
        givenValidToken()

        interceptor.handleConnectionInitialization(
            sessionInfo(),
            mapOf("Authorization" to "Bearer valid"),
        ).block()

        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
