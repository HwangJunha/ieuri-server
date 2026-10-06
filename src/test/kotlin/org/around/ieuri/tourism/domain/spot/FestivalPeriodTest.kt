package org.around.ieuri.tourism.domain.spot

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 축제 기간의 불변식과 종료 판정(SPEC-0007 R-4·R-8). */
class FestivalPeriodTest {

    private val runDate = LocalDate.of(2026, 8, 3)

    @Test
    fun `종료일이 시작일보다 앞서면 만들 수 없다`() {
        // AC-15.
        assertFailsWith<IllegalArgumentException> {
            FestivalPeriod(start = LocalDate.of(2026, 8, 10), end = LocalDate.of(2026, 8, 1))
        }
    }

    @Test
    fun `시작일과 종료일이 같은 당일 행사는 유효하다`() {
        // AC-11의 전제. 하루짜리 행사를 거부하면 안 된다.
        val period = FestivalPeriod(start = runDate, end = runDate)

        assertEquals(runDate, period.start)
        assertEquals(runDate, period.end)
    }

    @Test
    fun `연도를 넘는 기간도 정상이다`() {
        // AC-16. 연말연시 행사.
        val period = FestivalPeriod(start = LocalDate.of(2025, 12, 19), end = LocalDate.of(2026, 1, 3))

        assertFalse(period.isEndedAsOf(LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun `종료일이 기준일보다 하루 이전이면 종료된 것이다`() {
        // AC-26.
        val period = FestivalPeriod(start = LocalDate.of(2026, 7, 1), end = runDate.minusDays(1))

        assertTrue(period.isEndedAsOf(runDate))
    }

    @Test
    fun `종료일이 기준일과 같으면 아직 종료가 아니다`() {
        // AC-27. 경계 조건 — 오늘 끝나는 행사는 오늘 열려 있다.
        val period = FestivalPeriod(start = LocalDate.of(2026, 7, 1), end = runDate)

        assertFalse(period.isEndedAsOf(runDate))
    }

    @Test
    fun `종료일이 기준일보다 미래면 종료가 아니다`() {
        // AC-28.
        val period = FestivalPeriod(start = runDate, end = runDate.plusDays(7))

        assertFalse(period.isEndedAsOf(runDate))
    }
}
