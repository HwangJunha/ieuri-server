package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 원본 루트는 저장된 뒤에만 자식을 만들고, 만든 자식은 원본 id를 가진다. */
class SpotTest {

    private fun spot(id: Long?) = Spot(
        contentId = "C-1",
        language = DomainLocale.JA,
        title = "景福宮",
        address = null,
        thumbnailImageUrl = "https://img/1.jpg",
        coordinates = Coordinates(latitude = 37.5, longitude = 127.0),
        category = SpotCategory("TOURIST_SPOT"),
        contentTypeId = 76,
        copyright = CopyrightType.TYPE1,
        id = id,
    )

    private val detail = SpotDetail("景福宮", SpotCategory("TOURIST_SPOT"), "https://img/1.jpg", null, null, null, null, CopyrightType.TYPE1)

    @Test
    fun `자식은 원본 id를 가진다`() {
        val spot = spot(id = 7L)
        val category = SpotCategory("RESTAURANT")
        val period = FestivalPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))

        assertEquals(SpotBody(7L, detail, absent = false), spot.describe(detail))
        assertEquals(SpotBody(7L, detail, absent = true), spot.describeAbsence(detail))
        assertEquals(SpotGallery(7L, emptyList()), spot.showcase(emptyList()))
        assertEquals(SpotIntroduction(7L, category, null), spot.introduce(category, null))
        assertEquals(SpotInformation(7L, category, emptyList()), spot.inform(category, emptyList()))
        assertEquals(SpotFestival(7L, period, "진행", null), spot.hold(period, "진행", null))
    }

    @Test
    fun `저장되지 않은 원본은 자식을 만들지 않는다`() {
        val unsaved = spot(id = null)

        assertFailsWith<SpotNotSavedException> { unsaved.describe(detail) }
        assertFailsWith<SpotNotSavedException> { unsaved.hold(FestivalPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1)), null, null) }
    }
}
