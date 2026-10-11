package org.around.ieuri.auth.domain.token.spec

import org.around.ieuri.auth.domain.token.SocketIdentity
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 구독 인가 판정(SPEC-0032 R7, POL-048). 연결 수명이 액세스 TTL을 넘기거나 토큰이 전부 폐기됐으면 새 구독을 거부한다. */
class LiveSubscriptionIdentityTest {

    private val connectedAt = Instant.parse("2026-08-25T00:00:00Z")
    private val accessTtl: Duration = Duration.ofMinutes(5)
    private val live = SocketIdentity(connectedAt, hasActiveToken = true)

    private fun at(now: Instant) = LiveSubscriptionIdentity(accessTtl, now)

    @Test
    fun `연결 직후와 수명 안에서는 구독할 수 있다`() {
        assertTrue(at(connectedAt).isSatisfiedBy(live))
        assertTrue(at(connectedAt.plusSeconds(60)).isSatisfiedBy(live))
    }

    /** 경계는 아직 초과가 아니다 — 토큰 만료 판정과 같은 방향으로 맞춘다. */
    @Test
    fun `정확히 TTL 시점은 아직 만료가 아니다`() {
        assertTrue(at(connectedAt.plus(accessTtl)).isSatisfiedBy(live))
    }

    @Test
    fun `TTL을 1초라도 넘기면 토큰이 살아 있어도 거부한다`() {
        assertFalse(at(connectedAt.plus(accessTtl).plusSeconds(1)).isSatisfiedBy(live))
    }

    /** 탈퇴·정지·재사용 감지가 모두 revokeAllByMember를 부르므로 이 한 값이 세 경우를 막는다. */
    @Test
    fun `토큰이 전부 폐기됐으면 수명 안이어도 거부한다`() {
        assertFalse(at(connectedAt.plusSeconds(1)).isSatisfiedBy(live.copy(hasActiveToken = false)))
    }
}
