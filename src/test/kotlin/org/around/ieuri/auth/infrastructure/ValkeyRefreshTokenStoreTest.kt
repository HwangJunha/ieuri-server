package org.around.ieuri.auth.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.infrastructure.adapter.out.token.ValkeyRefreshTokenStore
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * ValkeyRefreshTokenStore — 외부 Valkey 없이 StringRedisTemplate을 대역으로 검증하는 순수 단위 테스트.
 * 실제 저장/TTL 축출은 Valkey 책임이므로 스코프에서 제외하고, 어댑터 자체 로직만 본다:
 *   - 만료 토큰 저장 스킵, 남은 TTL로 SET
 *   - 인프라 예외는 잡지 않고 그대로 전파 → existsActive는 장애를 false로 삼키지 않는다(fail-closed, 요건 #5)
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

    @Test
    fun `existsActive는 연결 장애를 false로 삼키지 않고 예외를 그대로 전파한다`() {
        every { redis.hasKey(any()) } throws RedisConnectionFailureException("down")

        assertFailsWith<RedisConnectionFailureException> { store.existsActive("jti-1") }
    }

    @Test
    fun `revokeAllByMember는 회원 인덱스의 모든 jti 키와 인덱스를 삭제한다`() {
        every { setOps.members("ieuri:auth:refresh:member:100") } returns setOf("jti-1", "jti-2")

        store.revokeAllByMember(100L)

        verify { redis.delete(listOf("ieuri:auth:refresh:jti:jti-1", "ieuri:auth:refresh:jti:jti-2")) }
        verify { redis.delete("ieuri:auth:refresh:member:100") }
    }
}
