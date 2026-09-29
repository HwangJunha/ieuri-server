package org.around.ieuri.auth.domain

import org.around.ieuri.auth.domain.specification.AuditRetention
import java.time.Duration
import java.time.Instant
import java.time.Period
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 감사 기록 보관 기간 판정(SPEC-0032 R22, POL-014). 경계는 배타적이고 기간은 인자로 받는다. */
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

    /** 위치정보법 제16조 제2항의 6개월. 법정 기간이라 값을 고정한다. */
    @Test
    fun `위치 조회 기록 보관 기간은 6개월이다`() {
        assertEquals(Period.ofMonths(6), AuditRetention.LOCATION_ACCESS)
    }

    /** 달의 길이를 따라야 한다. 고정 일수(180일)로 세면 고지한 6개월보다 이르게 지운다. */
    @Test
    fun `6개월 경계는 고정 일수가 아니라 달력으로 센다`() {
        // 2026-08-25의 6개월 전은 2026-02-25로, 180일 전(2026-02-26)보다 하루 이르다.
        val cutoff = AuditRetention.cutoff(now, AuditRetention.LOCATION_ACCESS)

        assertEquals(Instant.parse("2026-02-25T00:00:00Z"), cutoff)
        assertTrue(cutoff.isBefore(now.minus(Duration.ofDays(180))))
    }

    /** 판정이 기간을 인자로 받는다. */
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
