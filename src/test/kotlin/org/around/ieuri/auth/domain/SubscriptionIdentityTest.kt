package org.around.ieuri.auth.domain

import org.around.ieuri.auth.domain.specification.SubscriptionIdentity
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 구독 인가 판정(SPEC-0032 R7, POL-048).
 *
 * 고정하는 계약은 둘이다 — **연결 수명이 액세스 TTL을 넘기면 새 구독을 거부**하고,
 * **토큰이 전부 폐기됐으면 시간과 무관하게 거부**한다.
 *
 * 전송 계층 결합(프레임 수준 SecurityContext 전파)은 여기서 검증하지 않는다 — 실제 소켓이
 * 있어야 드러나므로 판정 로직만 순수 함수로 떼어 본다.
 */
class SubscriptionIdentityTest {

    private val connectedAt = Instant.parse("2026-08-25T00:00:00Z")
    private val accessTtl: Duration = Duration.ofMinutes(5)

    @Test
    fun `연결 직후에는 만료가 아니다`() {
        assertFalse(SubscriptionIdentity.isExpired(connectedAt, connectedAt, accessTtl))
    }

    /** 경계는 아직 초과가 아니다 — 토큰 만료 판정과 같은 방향으로 맞춘다. */
    @Test
    fun `정확히 TTL 시점은 아직 만료가 아니다`() {
        val now = connectedAt.plus(accessTtl)

        assertFalse(SubscriptionIdentity.isExpired(connectedAt, now, accessTtl))
    }

    @Test
    fun `TTL을 1초라도 넘기면 만료다`() {
        val now = connectedAt.plus(accessTtl).plusSeconds(1)

        assertTrue(SubscriptionIdentity.isExpired(connectedAt, now, accessTtl))
    }

    @Test
    fun `수명 안이고 토큰이 살아 있으면 구독할 수 있다`() {
        assertTrue(
            SubscriptionIdentity.canSubscribe(
                connectedAt = connectedAt,
                now = connectedAt.plusSeconds(60),
                accessTtl = accessTtl,
                hasActiveToken = true,
            ),
        )
    }

    /**
     * 탈퇴·정지·재사용 감지가 모두 revokeAllByMember를 부르므로, 이 한 값이 세 경우를 함께
     * 막는다. **연결이 살아 있다는 것이 자격의 증명은 아니다.**
     */
    @Test
    fun `토큰이 전부 폐기됐으면 수명 안이어도 거부한다`() {
        assertFalse(
            SubscriptionIdentity.canSubscribe(
                connectedAt = connectedAt,
                now = connectedAt.plusSeconds(1),
                accessTtl = accessTtl,
                hasActiveToken = false,
            ),
        )
    }

    @Test
    fun `수명을 넘기면 토큰이 살아 있어도 거부한다`() {
        assertFalse(
            SubscriptionIdentity.canSubscribe(
                connectedAt = connectedAt,
                now = connectedAt.plus(accessTtl).plusSeconds(1),
                accessTtl = accessTtl,
                hasActiveToken = true,
            ),
        )
    }
}
