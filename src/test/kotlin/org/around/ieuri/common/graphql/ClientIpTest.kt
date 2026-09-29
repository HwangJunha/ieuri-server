package org.around.ieuri.common.graphql

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DgsRequestData
import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 접속지 정보 판정: Cloudflare 헤더가 우선, X-Forwarded-For는 맨 앞, 무슨 값이 오든 던지지 않는다.
 */
class ClientIpTest {

    private fun dfe(vararg headerPairs: Pair<String, String>): DataFetchingEnvironment {
        val headers = HttpHeaders().apply { headerPairs.forEach { (k, v) -> add(k, v) } }
        val requestData = mockk<DgsRequestData> { every { this@mockk.headers } returns headers }
        // DgsContext가 Consumer<GraphQLContext.Builder>로서 자기 키를 직접 심는다.
        val context = GraphQLContext.newContext()
            .also { DgsContext(null, requestData).accept(it) }
            .build()
        return mockk { every { graphQlContext } returns context }
    }

    /** 헤더 자체가 없는 실행 컨텍스트(구독·테스트 등). */
    private fun dfeWithoutRequest(): DataFetchingEnvironment {
        val context = GraphQLContext.newContext().build()
        return mockk { every { graphQlContext } returns context }
    }

    @Test
    fun `CF-Connecting-IP를 쓴다`() {
        assertEquals("203.0.113.7", dfe("CF-Connecting-IP" to "203.0.113.7").clientIp())
    }

    /** 클라이언트가 X-Forwarded-For를 위조해 보내도 Cloudflare 헤더가 이긴다. */
    @Test
    fun `두 헤더가 모두 있으면 Cloudflare 헤더가 이긴다`() {
        val ip = dfe(
            "CF-Connecting-IP" to "203.0.113.7",
            "X-Forwarded-For" to "198.51.100.1",
        ).clientIp()

        assertEquals("203.0.113.7", ip)
    }

    /** 목록의 맨 앞이 최초 클라이언트다. 뒤쪽은 경유한 프록시들이다. */
    @Test
    fun `X-Forwarded-For는 맨 앞 항목을 쓴다`() {
        val ip = dfe("X-Forwarded-For" to "198.51.100.1, 70.41.3.18, 150.172.238.178").clientIp()

        assertEquals("198.51.100.1", ip)
    }

    @Test
    fun `IPv6도 그대로 담는다`() {
        val ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334"

        assertEquals(ipv6, dfe("CF-Connecting-IP" to ipv6).clientIp())
    }

    @Test
    fun `헤더가 없으면 null이다`() {
        assertNull(dfe().clientIp())
    }

    /** 구독처럼 요청 데이터가 없는 실행 컨텍스트에서도 던지지 않는다. */
    @Test
    fun `요청 데이터가 없어도 null을 돌려줄 뿐 던지지 않는다`() {
        assertNull(dfeWithoutRequest().clientIp())
    }

    @Test
    fun `빈 헤더는 없는 것으로 본다`() {
        assertNull(dfe("CF-Connecting-IP" to "   ").clientIp())
    }

    /** 값의 형식을 검증하지 않는다. 컬럼 길이만 지킨다. */
    @Test
    fun `IP 형식이 아니어도 던지지 않고 컬럼 길이만 지킨다`() {
        val garbage = "not-an-ip-" + "x".repeat(100)

        val ip = dfe("CF-Connecting-IP" to garbage).clientIp()

        assertEquals(45, ip?.length)
    }

    @Test
    fun `X-Forwarded-For가 쉼표뿐이어도 던지지 않는다`() {
        assertNull(dfe("X-Forwarded-For" to ",,,").clientIp())
    }
}
