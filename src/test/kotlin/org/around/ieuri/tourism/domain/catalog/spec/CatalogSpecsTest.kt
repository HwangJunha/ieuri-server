package org.around.ieuri.tourism.domain.catalog.spec

import org.around.ieuri.tourism.domain.catalog.CatalogDistrict
import org.around.ieuri.tourism.domain.catalog.TranslationPresence
import org.around.ieuri.tourism.domain.shared.DomainLocale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 카탈로그 일괄 반영이 쓰는 명세 — 노출은 그 언어의 원본이 있을 때, 시군구 귀속 대상은 아직 비어 있을 때. */
class CatalogSpecsTest {

    @Test
    fun `그 언어의 원본이 붙어 있으면 번역이 보인다`() {
        assertTrue(TranslationVisible.isSatisfiedBy(TranslationPresence(DomainLocale.JA, setOf(DomainLocale.KO, DomainLocale.JA))))
    }

    @Test
    fun `그 언어의 원본이 없으면 번역이 감춰진다`() {
        assertFalse(TranslationVisible.isSatisfiedBy(TranslationPresence(DomainLocale.ZH, setOf(DomainLocale.KO, DomainLocale.JA))))
        assertFalse(TranslationVisible.isSatisfiedBy(TranslationPresence(DomainLocale.KO, emptySet())))
    }

    @Test
    fun `시군구가 비어 있는 카탈로그만 귀속 대상이다`() {
        assertTrue(DistrictUnassigned.isSatisfiedBy(CatalogDistrict(catalogId = 1, districtCode = null)))
        assertFalse(DistrictUnassigned.isSatisfiedBy(CatalogDistrict(catalogId = 2, districtCode = "11110")))
    }
}
