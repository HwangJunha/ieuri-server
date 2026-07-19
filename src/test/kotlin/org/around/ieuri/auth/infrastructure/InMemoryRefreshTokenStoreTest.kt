package org.around.ieuri.auth.infrastructure

import org.around.ieuri.auth.infrastructure.adapter.out.token.InMemoryRefreshTokenStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** InMemoryRefreshTokenStore — 저장/조회/폐기/회원 전체 폐기/만료 정리 검증(순수). */
class InMemoryRefreshTokenStoreTest {

    private val now = Instant.parse("2026-07-17T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val store = InMemoryRefreshTokenStore(clock)

    @Test
    fun `저장한 미만료 jti는 active다`() {
        store.save("jti-1", 100L, now.plusSeconds(600))
        assertTrue(store.existsActive("jti-1"))
    }

    @Test
    fun `모르는 jti는 active가 아니다`() {
        assertFalse(store.existsActive("unknown"))
    }

    @Test
    fun `폐기하면 active가 아니다`() {
        store.save("jti-1", 100L, now.plusSeconds(600))
        store.revoke("jti-1")
        assertFalse(store.existsActive("jti-1"))
    }

    @Test
    fun `회원 전체 폐기 시 그 회원의 모든 jti가 무효화된다`() {
        store.save("jti-1", 100L, now.plusSeconds(600))
        store.save("jti-2", 100L, now.plusSeconds(600))
        store.save("jti-3", 200L, now.plusSeconds(600))

        store.revokeAllByMember(100L)

        assertFalse(store.existsActive("jti-1"))
        assertFalse(store.existsActive("jti-2"))
        assertTrue(store.existsActive("jti-3")) // 다른 회원은 영향 없음
    }

    @Test
    fun `만료된 jti는 active가 아니며 지연 정리된다`() {
        store.save("expired", 100L, now.minusSeconds(1))
        assertFalse(store.existsActive("expired"))
    }
}
