package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** 끝난 축제 — 종료일이 기준일보다 이전이면 끝났다. 종료일 당일은 진행 중이다. */
class EndedFestivalsTest {

    private val asOf = LocalDate.of(2026, 8, 10)

    private val ended = EndedFestivals(
        periods = mapOf(
            "yesterday" to FestivalPeriod(asOf.minusDays(5), asOf.minusDays(1)),
            "today" to FestivalPeriod(asOf.minusDays(5), asOf),
            "future" to FestivalPeriod(asOf.plusDays(1), asOf.plusDays(3)),
        ),
        asOf = asOf,
    )

    @Test
    fun `종료일이 기준일 전인 축제만 끝났다`() {
        assertEquals(setOf("yesterday"), ended.contentIds)
    }

    @Test
    fun `끝난 축제는 빼고 나머지는 순서대로 남긴다`() {
        val spots = listOf(spot("future"), spot("yesterday"), spot("plain"), spot("today"))

        assertEquals(listOf("future", "plain", "today"), ended.excludeFrom(spots).map { it.contentId })
    }

    private fun spot(contentId: String) = Spot(
        contentId = contentId,
        language = DomainLocale.KO,
        title = "t-$contentId",
        address = null,
        thumbnailImageUrl = "https://img/$contentId.jpg",
        coordinates = Coordinates(latitude = 37.5, longitude = 127.0),
        category = SpotCategory.FESTIVAL_EVENT,
        contentTypeId = 15,
        copyright = CopyrightType.TYPE1,
    )
}
