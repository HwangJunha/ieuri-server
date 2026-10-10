package org.around.ieuri.auth.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.adapter.token.ValkeyRefreshTokenStore
import org.around.ieuri.auth.application.token.provided.exception.AuthStoreUnavailableException
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * ValkeyRefreshTokenStore — StringRedisTemplate 대역으로 어댑터 로직만 본다.
 * - 만료 토큰 저장 스킵, 남은 TTL로 SET, 인프라 예외는 AuthStoreUnavailableException으로 올린다(fail-closed, SPEC-0032 R6).
 */
class ValkeyRefreshTokenStoreTest {

    private val now = Instant.parse("2026-07-17T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val setOps = mockk<SetOperations<String, String>>(relaxed = true)
    private val redis = mockk<StringRedisTemplate>(relaxed = true).also {
        every { it.opsForValue() } returns valueOps
        every { it.opsForSet() } returns setOps
    }
    private val store = ValkeyRefreshTokenStore(redis, clock)

    @Test
    fun `저장 시 남은 TTL로 jti 키를 SET하고 회원 인덱스에 추가한다`() {
        store.save("jti-1", 100L, now.plusSeconds(600))

        verify { valueOps.set("ieuri:auth:refresh:jti:jti-1", "100", Duration.ofSeconds(600)) }
        verify { setOps.add("ieuri:auth:refresh:member:100", "jti-1") }
        verify { redis.expire("ieuri:auth:refresh:member:100", Duration.ofSeconds(600)) }
    }

    @Test
    fun `이미 만료된 토큰은 저장하지 않는다`() {
        store.save("expired", 100L, now.minusSeconds(1))

        verify(exactly = 0) { valueOps.set(any(), any(), any<Duration>()) }
        verify(exactly = 0) { setOps.add(any(), any()) }
    }

    @Test
    fun `existsActive는 키 존재 여부를 그대로 반환한다`() {
        every { redis.hasKey("ieuri:auth:refresh:jti:jti-1") } returns true
        assertTrue(store.existsActive("jti-1"))
    }

    /** fail-closed: 장애를 false로 삼키지 않고 코드를 실어 올린다(SPEC-0032 R6). */
    @Test
    fun `existsActive는 연결 장애를 false로 삼키지 않고 저장소 장애로 올린다`() {
        every { redis.hasKey(any()) } throws RedisConnectionFailureException("down")

        val e = assertFailsWith<AuthStoreUnavailableException> { store.existsActive("jti-1") }

        assertEquals("AUTH_STORE_UNAVAILABLE", e.code)
        // 원인은 cause로만 남긴다 — 메시지에 키나 내부 정보를 담지 않는다(POL-013).
        assertTrue(e.cause is RedisConnectionFailureException)
    }

    /** 쓰기 경로도 같은 코드로 올라온다 — 회전 중 저장 실패가 INTERNAL_ERROR가 되지 않는다. */
    @Test
    fun `save도 연결 장애를 저장소 장애로 올린다`() {
        every { valueOps.set(any(), any(), any<Duration>()) } throws RedisConnectionFailureException("down")

        assertFailsWith<AuthStoreUnavailableException> {
            store.save("jti-1", 100L, now.plusSeconds(600))
        }
    }

    @Test
    fun `revokeAllByMember는 회원 인덱스의 모든 jti 키와 인덱스를 삭제한다`() {
        every { setOps.members("ieuri:auth:refresh:member:100") } returns setOf("jti-1", "jti-2")

        store.revokeAllByMember(100L)

        verify { redis.delete(listOf("ieuri:auth:refresh:jti:jti-1", "ieuri:auth:refresh:jti:jti-2")) }
        verify { redis.delete("ieuri:auth:refresh:member:100") }
    }
}
