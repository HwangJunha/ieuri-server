package org.around.ieuri.tourism.domain.spot.spec

import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 끝난 축제 — 종료일이 기준일보다 이전이면 끝났다. 종료일 당일은 진행 중이다. */
class EndedFestivalTest {

    private val asOf = LocalDate.of(2026, 8, 10)

    private val ended = EndedFestival(
        periods = mapOf(
            "yesterday" to FestivalPeriod(asOf.minusDays(5), asOf.minusDays(1)),
            "today" to FestivalPeriod(asOf.minusDays(5), asOf),
            "future" to FestivalPeriod(asOf.plusDays(1), asOf.plusDays(3)),
        ),
        asOf = asOf,
    )

    @Test
    fun `종료일이 기준일 전인 축제는 끝났다`() {
        assertTrue(ended.isSatisfiedBy("yesterday"))
    }

    @Test
    fun `종료일 당일과 시작 전인 축제는 끝나지 않았다`() {
        assertFalse(ended.isSatisfiedBy("today"))
        assertFalse(ended.isSatisfiedBy("future"))
    }

    @Test
    fun `기간을 모르는 원본은 끝나지 않았다`() {
        assertFalse(ended.isSatisfiedBy("plain"))
    }
}
