package org.around.ieuri.auth

import io.mockk.mockk
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import org.around.ieuri.auth.infrastructure.security.GraphqlWebSocketAuthInterceptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.graphql.server.WebSocketGraphQlRequest
import org.springframework.graphql.server.WebSocketSessionInfo
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.LinkedMultiValueMap
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * WebSocket 요청의 인증 주체 전달(SPEC-0017 R10·R14, POL-048).
 * - 리졸버가 실행되는 구독 시점에 신원이 보여야 하고, 끝나면 반드시 지워져야 한다.
 * - `chain.next()`가 콜드 Mono라 try/finally면 앞의 것이, defer만 하면 뒤의 것이 깨진다.
 */
class GraphqlWebSocketAuthInterceptorTest {

    private val interceptor = GraphqlWebSocketAuthInterceptor(
        mockk<TokenProviderPort>(relaxed = true),
        Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC),
    )

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun request(memberId: Long?): WebSocketGraphQlRequest {
        val attributes = buildMap<String, Any> {
            if (memberId != null) {
                put("ieuri.memberId", memberId)
                put("ieuri.permissions", listOf("community:read"))
            }
        }
        val sessionInfo = object : WebSocketSessionInfo {
            override fun getId() = "session-1"
            override fun getUri() = URI.create("ws://localhost:8888/graphql")
            override fun getHeaders() = HttpHeaders()
            override fun getAttributes() = attributes
            override fun getPrincipal() = Mono.empty<java.security.Principal>()
            override fun getRemoteAddress() = null
        }
        return WebSocketGraphQlRequest(
            URI.create("ws://localhost:8888/graphql"),
            HttpHeaders(),
            LinkedMultiValueMap(),
            // 원격 주소. 위 sessionInfo.getRemoteAddress()와 맞춰 null이다.
            null,
            emptyMap(),
            mapOf("query" to "subscription { newMessage(conversationId: 1) { id } }"),
            "1",
            null,
            sessionInfo,
        )
    }

    /** 체인이 실행될 때 principal을 기록하는 스텁. `Mono.fromCallable`이라 조립과 구독 시점이 갈린다. */
    private fun recordingChain(seen: MutableList<Any?>) = WebGraphQlInterceptor.Chain {
        Mono.fromCallable {
            seen += SecurityContextHolder.getContext().authentication?.principal
            mockk<WebGraphQlResponse>(relaxed = true)
        }
    }

    /** **리졸버가 도는 구독 시점에** 신원이 보여야 한다. 안 보이면 모든 구독이 실패한다. */
    @Test
    fun `구독 시점에 인증 주체가 보인다`() {
        val seen = mutableListOf<Any?>()

        interceptor.intercept(request(memberId = 42L), recordingChain(seen)).block()

        assertEquals(listOf<Any?>(42L), seen)
    }

    /** 끝나면 지워야 한다 — 스레드가 재사용되므로 남기면 다음 요청이 물려받는다. */
    @Test
    fun `처리가 끝나면 컨텍스트를 지운다`() {
        interceptor.intercept(request(memberId = 42L), recordingChain(mutableListOf())).block()

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    /** 인증 정보가 없으면 비워 둔다. 스레드가 재사용되므로 앞 요청의 신원으로 통과하면 안 된다. */
    @Test
    fun `인증 정보가 없으면 앞 요청의 신원을 물려받지 않는다`() {
        val seen = mutableListOf<Any?>()
        interceptor.intercept(request(memberId = 42L), recordingChain(mutableListOf())).block()

        interceptor.intercept(request(memberId = null), recordingChain(seen)).block()

        assertEquals(listOf<Any?>(null), seen)
    }
}
