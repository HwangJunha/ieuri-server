package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.aggregate.CatalogFestivalPeriod
import org.around.ieuri.tourism.domain.aggregate.CatalogStandardValues
import org.around.ieuri.tourism.domain.aggregate.CatalogTranslation
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 카탈로그 애그리거트(SPEC-0019).
 *
 * 이 스펙의 핵심은 **카탈로그가 이름을 갖지 않는다**는 것이다 — 이름은 언어에 종속되므로
 * 번역에만 있고, 그래야 "한국어 이름이 없는 장소"를 표현할 수 있다.
 */
class SpotCatalogTest {

    private fun standard(
        mapX: Double = 129.1234,
        mapY: Double = 35.5678,
    ) = CatalogStandardValues(
        mapX = mapX,
        mapY = mapY,
        thumbnailImageUrl = "https://img/1.jpg",
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.TYPE1,
    )

    @Test
    @DisplayName("KO spot에서 만든 카탈로그는 KO 번역만 갖는다")
    fun `korean spot yields a KO translation`() {
        val catalog = SpotCatalog.from(
            language = DomainLocale.KO,
            title = "마린시티",
            address = "부산광역시 해운대구",
            standard = standard(),
        )

        assertEquals("마린시티", catalog.translations[DomainLocale.KO]?.title)
        assertEquals("부산광역시 해운대구", catalog.translations[DomainLocale.KO]?.address)
        assertEquals(1, catalog.translations.size)
    }

    @Test
    @DisplayName("외국어 전용 장소는 한국어 번역 없이 존재한다")
    fun `foreign only spot has no korean translation`() {
        // **이 스펙의 핵심 판단이다.** 원본에 KO가 없는 장소에 한국어명을 지어내면 정합성이
        // 깨진다. 그 장소는 자기 언어 사용자에게만 보이면 된다.
        val catalog = SpotCatalog.from(
            language = DomainLocale.EN,
            title = "SM Entertainment",
            address = "Seoul",
            standard = standard(),
        )

        assertNull(catalog.translations[DomainLocale.KO])
        assertTrue(catalog.needsTranslation(DomainLocale.KO))
        assertEquals("SM Entertainment", catalog.translations[DomainLocale.EN]?.title)
    }

    @Test
    @DisplayName("빈 제목으로는 카탈로그를 만들 수 없다")
    fun `blank title is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotCatalog.from(DomainLocale.KO, "   ", null, standard())
        }
    }

    @Test
    @DisplayName("번역이 없는 언어만 번역 대상이다")
    fun `only missing languages need translation`() {
        val catalog = SpotCatalog.from(DomainLocale.KO, "마린시티", null, standard())

        assertFalse(catalog.needsTranslation(DomainLocale.KO))
        assertTrue(catalog.needsTranslation(DomainLocale.JA))
    }

    @Test
    @DisplayName("번역을 채우면 그 언어는 더 이상 대상이 아니다")
    fun `filling a translation clears the need`() {
        val catalog = SpotCatalog.from(DomainLocale.KO, "마린시티", null, standard())

        catalog.fillTranslation(DomainLocale.JA, "マリンシティ", "釜山広域市 海雲台区")

        assertFalse(catalog.needsTranslation(DomainLocale.JA))
        assertEquals("マリンシティ", catalog.translations[DomainLocale.JA]?.title)
        assertEquals("釜山広域市 海雲台区", catalog.translations[DomainLocale.JA]?.address)
    }

    @Test
    @DisplayName("이미 값이 있는 언어는 덮어쓰지 않는다")
    fun `existing translation is never overwritten`() {
        // 먼저 들어온 값이 TourAPI 원문이고 그것이 우리 번역보다 우선한다(R-4).
        val catalog = SpotCatalog.from(DomainLocale.KO, "마린시티", null, standard())

        catalog.fillTranslation(DomainLocale.KO, "덮어쓴 제목")

        assertEquals("마린시티", catalog.translations[DomainLocale.KO]?.title)
    }

    @Test
    @DisplayName("표준값을 갱신할 수 있다")
    fun `standard values can be updated`() {
        val catalog = SpotCatalog.from(DomainLocale.KO, "마린시티", null, standard())

        catalog.updateStandard(standard(mapX = 127.0, mapY = 37.5))

        assertEquals(127.0, catalog.standard.mapX)
        assertEquals(37.5, catalog.standard.mapY)
    }

    @Test
    @DisplayName("축제 기간을 확정하고 지울 수 있다")
    fun `festival period can be set and cleared`() {
        val catalog = SpotCatalog.from(DomainLocale.KO, "불꽃축제", null, standard())
        val period = CatalogFestivalPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))

        catalog.updateFestivalPeriod(period)
        assertEquals(period, catalog.festivalPeriod)

        // 상류 정리로 원본이 사라지면 축제가 아니게 된다.
        catalog.updateFestivalPeriod(null)
        assertNull(catalog.festivalPeriod)
    }

    @Test
    @DisplayName("복원한 카탈로그는 저장된 값을 그대로 갖는다")
    fun `restore keeps stored values`() {
        val catalog = SpotCatalog.restore(
            id = 4321,
            standard = standard(),
            translations = mapOf(
                DomainLocale.KO to CatalogTranslation("마린시티", "부산광역시 해운대구"),
                DomainLocale.JA to CatalogTranslation("マリンシティ", "釜山広域市"),
            ),
        )

        assertEquals(4321L, catalog.id)
        assertEquals(2, catalog.translations.size)
        assertEquals("マリンシティ", catalog.translations[DomainLocale.JA]?.title)
    }

    @Test
    @DisplayName("한국 밖 좌표로는 표준값을 만들 수 없다")
    fun `coordinates outside korea are rejected`() {
        // 좌표가 (0,0)이거나 한국 밖인 원본은 위치를 모르는 장소가 아니라 깨진 행이다.
        assertThrows(IllegalArgumentException::class.java) { standard(mapX = 0.0, mapY = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { standard(mapX = 139.7, mapY = 35.7) }
    }

    @Test
    @DisplayName("좌표 유효성 판정은 한국 범위를 기준으로 한다")
    fun `coordinate validity follows the korea range`() {
        assertTrue(CatalogStandardValues.isValidCoordinate(129.1234, 35.5678))
        assertFalse(CatalogStandardValues.isValidCoordinate(0.0, 0.0))
        assertFalse(CatalogStandardValues.isValidCoordinate(139.7, 35.7))
    }

    @Test
    @DisplayName("축제 종료일은 시작일보다 빠를 수 없다")
    fun `festival period must not be inverted`() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogFestivalPeriod(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 1))
        }
    }
}
