package org.around.ieuri.auth.domain.audit

import java.time.Duration
import java.time.Instant
import java.time.Period
import kotlin.test.Test
import kotlin.test.assertEquals

/** 감사 기록 세 종의 파기 기준 시각. 법정 기간은 서울 달력의 연·월로 세고, 회원 감사 기록만 고정 일수다. */
class AuditLogRetentionTest {

    // ── 회원 감사 기록: 365일 ──

    @Test
    fun `회원 감사 기록은 365일 전이 기준이다`() {
        assertEquals(Duration.ofDays(365), MemberAuditLog.RETENTION)
        assertEquals(Instant.parse("2025-10-05T03:00:00Z"), MemberAuditLog.cutoff(Instant.parse("2026-10-05T03:00:00Z")))
    }

    @Test
    fun `회원 감사 기록은 윤일이 끼어도 달력 1년이 아니라 365일이다`() {
        assertEquals(Instant.parse("2023-03-02T00:00:00Z"), MemberAuditLog.cutoff(Instant.parse("2024-03-01T00:00:00Z")))
    }

    // ── 접속기록: 서울 달력 1년 ──

    @Test
    fun `접속기록은 윤일이 끼면 366일 전이 기준이다`() {
        assertEquals(Period.ofYears(1), MemberAccessLog.RETENTION)
        assertEquals(Instant.parse("2023-03-01T00:00:00Z"), MemberAccessLog.cutoff(Instant.parse("2024-03-01T00:00:00Z")))
    }

    /** UTC로는 2월 28일이지만 서울로는 3월 1일이다. UTC로 세면 하루 이른 2024-02-28이 나온다. */
    @Test
    fun `접속기록은 서울 날짜로 1년을 뺀다`() {
        assertEquals(Instant.parse("2024-02-29T15:30:00Z"), MemberAccessLog.cutoff(Instant.parse("2025-02-28T15:30:00Z")))
    }

    // ── 위치 조회 기록: 서울 달력 6개월 ──

    /** 8월 31일의 6개월 전은 2월 말일로 당겨진다. 180일로 세면 3월 4일이라 고지보다 이르게 지운다. */
    @Test
    fun `위치 조회 기록은 180일이 아니라 6개월 전이 기준이다`() {
        assertEquals(Period.ofMonths(6), LocationAccessLog.RETENTION)
        assertEquals(Instant.parse("2026-02-28T03:00:00Z"), LocationAccessLog.cutoff(Instant.parse("2026-08-31T03:00:00Z")))
        assertEquals(Instant.parse("2026-04-05T03:00:00Z"), LocationAccessLog.cutoff(Instant.parse("2026-10-05T03:00:00Z")))
    }

    /** UTC로는 8월 30일이지만 서울로는 8월 31일이다. UTC로 세면 2026-02-28T15:30Z가 나온다. */
    @Test
    fun `위치 조회 기록은 서울 날짜로 6개월을 뺀다`() {
        assertEquals(Instant.parse("2026-02-27T15:30:00Z"), LocationAccessLog.cutoff(Instant.parse("2026-08-30T15:30:00Z")))
    }
}
