package org.around.ieuri.auth.domain

import org.around.ieuri.auth.domain.specification.AuditRetention
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 감사 기록 보관 기간 판정(SPEC-0032 R22, POL-014).
 *
 * 고정하는 계약은 둘이다 — **경계는 배타적**(정확히 기간에 걸친 기록은 남는다)이고,
 * **기간을 인자로 받는다**(대상이 늘어도 판정 로직을 다시 쓰지 않는다).
 */
class AuditRetentionTest {

    private val now = Instant.parse("2026-08-25T00:00:00Z")

    @Test
    fun `회원 감사 로그 보관 기간은 1년이다`() {
        assertEquals(Duration.ofDays(365), AuditRetention.MEMBER_AUDIT)
    }

    /** 경계는 배타적이다 — 정확히 기간에 걸친 기록은 아직 삭제 대상이 아니다. */
    @Test
    fun `정확히 경계 시각의 기록은 아직 만료가 아니다`() {
        val createdAt = now.minus(AuditRetention.MEMBER_AUDIT)

        assertFalse(AuditRetention.isExpired(createdAt, now, AuditRetention.MEMBER_AUDIT))
    }

    @Test
    fun `경계보다 1초라도 이르면 만료다`() {
        val createdAt = now.minus(AuditRetention.MEMBER_AUDIT).minusSeconds(1)

        assertTrue(AuditRetention.isExpired(createdAt, now, AuditRetention.MEMBER_AUDIT))
    }

    @Test
    fun `방금 만든 기록은 만료가 아니다`() {
        assertFalse(AuditRetention.isExpired(now, now, AuditRetention.MEMBER_AUDIT))
    }

    /**
     * 판정이 기간을 인자로 받으므로, 위치 기록의 기간이 나중에 정해져도 이 로직을 다시 쓰지
     * 않는다. 위치 기록의 보관 기간 자체는 이 슬라이스가 정하지 않는다.
     */
    @Test
    fun `기간을 바꾸면 같은 기록의 판정도 바뀐다`() {
        val createdAt = now.minus(Duration.ofDays(100))

        assertFalse(AuditRetention.isExpired(createdAt, now, Duration.ofDays(365)))
        assertTrue(AuditRetention.isExpired(createdAt, now, Duration.ofDays(30)))
    }

    @Test
    fun `경계 시각은 현재에서 기간을 뺀 값이다`() {
        assertEquals(
            now.minus(Duration.ofDays(30)),
            AuditRetention.cutoff(now, Duration.ofDays(30)),
        )
    }
}
