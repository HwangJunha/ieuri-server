package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.CatalogNameSource
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.TranslationSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 카탈로그 애그리거트(SPEC-0019 R-3·R-4·R-5).
 */
class SpotCatalogTest {

    @Test
    @DisplayName("KO 행에서 만든 카탈로그는 대표명 출처가 OFFICIAL이다")
    fun `korean spot yields official name`() {
        val catalog = SpotCatalog.fromKoreanSpot("마린시티")

        assertEquals("마린시티", catalog.representativeNameKo)
        assertEquals(CatalogNameSource.OFFICIAL, catalog.nameSource)
    }

    @Test
    @DisplayName("KO 제목은 KO 번역 행으로도 선다")
    fun `korean title becomes the KO translation`() {
        // 조회가 spot_catalog_translation 하나만 보면 되게 하기 위해서다.
        val catalog = SpotCatalog.fromKoreanSpot("마린시티")

        val ko = catalog.translations[DomainLocale.KO]
        assertEquals("마린시티", ko?.title)
        assertEquals(TranslationSource.OFFICIAL, ko?.source)
    }

    @Test
    @DisplayName("KO 행이 없으면 괄호 한글에서 파생하고 출처는 DERIVED다")
    fun `derives korean name from foreign title`() {
        val catalog = SpotCatalog.deriveFromForeignTitles(
            mapOf(DomainLocale.EN to "Lacantina (라칸티나)"),
        )

        assertEquals("라칸티나", catalog?.representativeNameKo)
        assertEquals(CatalogNameSource.DERIVED, catalog?.nameSource)
    }

    @Test
    @DisplayName("괄호값이 언어마다 다르면 EN을 채택한다")
    fun `EN wins when bracket values differ`() {
        // 실측 163개 그룹에서 언어별 괄호값이 달랐다 — `초유향`/`초류향`.
        val catalog = SpotCatalog.deriveFromForeignTitles(
            mapOf(
                DomainLocale.JA to "チョユヒャン（초류향）",
                DomainLocale.EN to "Chouhyang (초유향)",
                DomainLocale.ZH to "楚流香（초류향）",
            ),
        )

        assertEquals("초유향", catalog?.representativeNameKo)
    }

    @Test
    @DisplayName("EN이 없으면 JA, JA도 없으면 ZH를 채택한다")
    fun `falls back to JA then ZH`() {
        // ZH만 702개, JA만 278개인 카탈로그가 실재해 EN 단독 규칙으로는 결정되지 않는다.
        val jaOnly = SpotCatalog.deriveFromForeignTitles(
            mapOf(
                DomainLocale.JA to "チョユヒャン（초류향）",
                DomainLocale.ZH to "楚流香（초유향）",
            ),
        )
        assertEquals("초류향", jaOnly?.representativeNameKo)

        val zhOnly = SpotCatalog.deriveFromForeignTitles(
            mapOf(DomainLocale.ZH to "楚流香（초유향）"),
        )
        assertEquals("초유향", zhOnly?.representativeNameKo)
    }

    @Test
    @DisplayName("어느 언어에서도 한국어를 못 뽑으면 파생하지 않는다")
    fun `returns null when no korean can be extracted`() {
        val catalog = SpotCatalog.deriveFromForeignTitles(
            mapOf(DomainLocale.EN to "SM Entertainment"),
        )

        assertNull(catalog)
    }

    @Test
    @DisplayName("파생 카탈로그의 원문 외국어 제목은 OFFICIAL로 선다")
    fun `derived catalog keeps foreign titles as official`() {
        // TourAPI가 발급한 제목이므로 번역 대상이 아니다.
        val catalog = SpotCatalog.deriveFromForeignTitles(
            mapOf(DomainLocale.EN to "Lacantina (라칸티나)"),
        )

        assertEquals(TranslationSource.OFFICIAL, catalog?.translations?.get(DomainLocale.EN)?.source)
        assertFalse(catalog!!.needsTranslation(DomainLocale.EN))
    }

    @Test
    @DisplayName("제목이 있는 언어는 번역 대상이 아니다")
    fun `existing language is not a translation target`() {
        val catalog = SpotCatalog.fromKoreanSpot("마린시티")

        assertFalse(catalog.needsTranslation(DomainLocale.KO))
        assertTrue(catalog.needsTranslation(DomainLocale.JA))
    }

    @Test
    @DisplayName("기계 번역은 이미 있는 제목을 덮지 않는다")
    fun `machine translation never overwrites`() {
        // TourAPI가 제공한 제목이 공식 표기이며 우리 번역보다 우선한다(R-4).
        val catalog = SpotCatalog.deriveFromForeignTitles(
            mapOf(DomainLocale.EN to "Lacantina (라칸티나)"),
        )!!

        catalog.fillMachineTranslation(DomainLocale.EN, "번역이 밀어낸 값")

        assertEquals("Lacantina (라칸티나)", catalog.translations[DomainLocale.EN]?.title)
        assertEquals(TranslationSource.OFFICIAL, catalog.translations[DomainLocale.EN]?.source)
    }

    @Test
    @DisplayName("비어 있는 언어에는 기계 번역이 채워진다")
    fun `machine translation fills an empty language`() {
        val catalog = SpotCatalog.fromKoreanSpot("마린시티")

        catalog.fillMachineTranslation(DomainLocale.JA, "マリンシティ")

        assertEquals("マリンシティ", catalog.translations[DomainLocale.JA]?.title)
        assertEquals(TranslationSource.MACHINE, catalog.translations[DomainLocale.JA]?.source)
    }

    @Test
    @DisplayName("공식 제목이 도착하면 기계 번역을 교체한다")
    fun `official title replaces machine translation`() {
        // 새 번역을 만드는 것이 아니라 원본이 도착해 파생값을 밀어내는 것이다(R-5).
        val catalog = SpotCatalog.fromKoreanSpot("마린시티")
        catalog.fillMachineTranslation(DomainLocale.JA, "マリンシティ")

        catalog.applyOfficialTitle(DomainLocale.JA, "マリンシティ（마린시티）")

        assertEquals("マリンシティ（마린시티）", catalog.translations[DomainLocale.JA]?.title)
        assertEquals(TranslationSource.OFFICIAL, catalog.translations[DomainLocale.JA]?.source)
    }
}
