package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class SpotCatalogTest {

    @Test
    @DisplayName("원본 하나로 만든 카탈로그는 그 언어 번역 하나와 원본의 표준값을 갖는다")
    fun `found takes standard values and one translation from the origin`() {
        val catalog = SpotCatalog.found(koreanOrigin(mapX = 127.11, mapY = 37.22))

        assertNull(catalog.id)
        assertEquals(127.11, catalog.standard.mapX)
        assertEquals(37.22, catalog.standard.mapY)
        assertEquals(THUMB, catalog.standard.thumbnailImageUrl)
        assertEquals(SpotCategory.TOURIST_SPOT, catalog.standard.category)
        assertEquals(CopyrightType.TYPE1, catalog.standard.copyright)
        val translation = catalog.translations.single()
        assertEquals(DomainLocale.KO, translation.language)
        assertEquals("마린시티", translation.title)
        assertEquals("부산", translation.address)
        assertTrue(translation.visible)
    }

    @Test
    @DisplayName("언어마다 번역은 하나다")
    fun `two translations in one language are rejected`() {
        assertThrows<IllegalArgumentException> { catalog(900, translation(DomainLocale.KO), translation(DomainLocale.KO, "다른 이름")) }
    }

    @Test
    @DisplayName("빈 제목의 번역은 만들 수 없다")
    fun `blank title is rejected`() {
        assertThrows<IllegalArgumentException> { translation(DomainLocale.KO, "  ") }
    }

    @Test
    @DisplayName("한국 밖 좌표로는 표준값을 만들 수 없다")
    fun `standard values outside korea are rejected`() {
        assertThrows<IllegalArgumentException> { CatalogStandardValues(139.7, 35.7, THUMB, SpotCategory.TOURIST_SPOT, CopyrightType.TYPE1) }
        assertTrue(CatalogStandardValues.isValidCoordinate(129.1234, 35.5678))
        assertFalse(CatalogStandardValues.isValidCoordinate(0.0, 0.0))
    }

    @Test
    @DisplayName("없는 언어의 원본이 붙으면 그 언어 번역이 생긴다")
    fun `attaching a new language adds its translation`() {
        val added = catalog().attach(origin(language = DomainLocale.EN, title = "Marine City (마린시티)"))

        assertEquals(CatalogTranslation(DomainLocale.EN, "Marine City", "Busan", visible = true), added)
    }

    @Test
    @DisplayName("있는 언어의 원본이 붙어도 제목·주소를 덮지 않는다")
    fun `attaching an existing language changes nothing`() {
        assertNull(catalog().attach(koreanOrigin(title = "새 이름")))
    }

    @Test
    @DisplayName("감춰졌던 언어의 원본이 돌아오면 번역을 되살린다")
    fun `attaching a hidden language revives it`() {
        val revived = catalog(900, translation(DomainLocale.KO, visible = false)).attach(koreanOrigin(title = "새 이름"))

        assertEquals(CatalogTranslation(DomainLocale.KO, "마린시티", null, visible = true), revived)
    }

    @Test
    @DisplayName("보이는 번역이 있는 언어에서만 반응할 수 있다")
    fun `react creates a reaction in a visible language`() {
        val reaction = catalog(900, translation(DomainLocale.EN, "Marine City")).react(7, DomainLocale.EN, ReactionType.DISLIKE)

        assertEquals(SpotReaction(memberId = 7, catalogId = 900, language = DomainLocale.EN, reaction = ReactionType.DISLIKE), reaction)
    }

    @Test
    @DisplayName("번역이 없거나 감춰진 언어에서는 볼 수 없는 장소다")
    fun `react in a missing or hidden language is rejected`() {
        assertThrows<SpotNotFoundInLocaleException> { catalog().react(7, DomainLocale.JA, ReactionType.LIKE) }
        assertThrows<SpotNotFoundInLocaleException> { catalog(900, translation(DomainLocale.KO, visible = false)).react(7, DomainLocale.KO, ReactionType.LIKE) }
    }

    @Test
    @DisplayName("원본이 한 군집이면 가르지 않는다")
    fun `split of one cluster is null`() {
        assertNull(catalog().split(listOf(koreanOrigin(spotId = 1), origin(spotId = 2))))
    }

    @Test
    @DisplayName("가장 큰 군집이 남고, 떠나는 군집은 새 카탈로그가 되며, 남는 쪽에 없는 언어 번역만 지운다")
    fun `split keeps the largest cluster and moves the rest`() {
        val far = 128.0
        val origins = listOf(
            koreanOrigin(spotId = 1),
            origin(spotId = 2, language = DomainLocale.EN),
            origin(spotId = 3, language = DomainLocale.JA, title = "マリンシティ", mapX = far),
            origin(spotId = 4, language = DomainLocale.ZH, title = "海雲台", mapX = far),
            origin(spotId = 5, language = DomainLocale.KO, title = "마린시티2", mapX = 127.0, mapY = 37.0),
        )

        val split = requireNotNull(catalog().split(origins))

        assertEquals(listOf(listOf(3L, 4L), listOf(5L)), split.departures.map { it.spotIds })
        assertEquals(listOf(DomainLocale.JA, DomainLocale.ZH), split.removedLanguages)
    }

    @Test
    @DisplayName("KO가 없는 군집은 언어 순서상 첫 원본에서 표준값과 제목을 가져오고, 나머지 언어 번역을 덧붙인다")
    fun `found from a cluster uses language priority`() {
        val catalog = SpotCatalog.foundFrom(
            listOf(
                origin(spotId = 3, language = DomainLocale.ZH, title = "海雲台", mapX = 128.1),
                origin(spotId = 4, language = DomainLocale.JA, title = "マリンシティ", mapX = 128.2),
                origin(spotId = 5, language = DomainLocale.JA, title = "マリン2", mapX = 128.3),
            ),
        )

        assertEquals(128.2, catalog.standard.mapX)
        assertEquals(listOf(DomainLocale.JA, DomainLocale.ZH), catalog.translations.map { it.language })
        assertEquals("マリンシティ", catalog.translations.first().title)
    }

    @Test
    @DisplayName("한글만 남는 제목도 분할에서는 원문 그대로 번역이 된다")
    fun `split translation falls back to the raw title`() {
        val catalog = SpotCatalog.foundFrom(listOf(origin(language = DomainLocale.EN, title = "마린시티")))

        assertEquals("마린시티", catalog.translations.single().title)
    }

    @Test
    @DisplayName("축제 기간은 언어 순서, 같은 언어면 원본 id 순으로 고른다")
    fun `festival period follows language priority then spot id`() {
        val day = LocalDate.of(2026, 10, 1)
        val periods = listOf(
            OriginFestivalPeriod(spotId = 9, language = DomainLocale.EN, period = FestivalPeriod(day, day.plusDays(1))),
            OriginFestivalPeriod(spotId = 8, language = DomainLocale.KO, period = FestivalPeriod(day, day.plusDays(2))),
            OriginFestivalPeriod(spotId = 7, language = DomainLocale.KO, period = FestivalPeriod(day, day.plusDays(3))),
        )

        assertEquals(day.plusDays(3), SpotCatalog.festivalPeriodOf(periods)?.end)
        assertNull(SpotCatalog.festivalPeriodOf(emptyList()))
    }
}
