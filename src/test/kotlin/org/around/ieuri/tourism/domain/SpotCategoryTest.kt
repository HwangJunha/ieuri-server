package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SpotCategory 언어별 가용성(AC-8/9)과 DomainLocale 폴백(AC-11). */
class SpotCategoryTest {

    @Test
    fun `KO는 여행코스를 포함하고 교통을 제외한다 - 총 8종`() {
        val ko = SpotCategory.availableIn(DomainLocale.KO)
        assertTrue(SpotCategory.TRAVEL_COURSE in ko)
        assertFalse(SpotCategory.TRANSPORT in ko)
        assertEquals(8, ko.size)
    }

    @Test
    fun `외국어(EN)는 교통을 포함하고 여행코스를 제외한다 - 총 8종`() {
        for (locale in listOf(DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)) {
            val cats = SpotCategory.availableIn(locale)
            assertTrue(SpotCategory.TRANSPORT in cats, "$locale should include TRANSPORT")
            assertFalse(SpotCategory.TRAVEL_COURSE in cats, "$locale should exclude TRAVEL_COURSE")
            assertEquals(8, cats.size, "$locale")
        }
    }

    @Test
    fun `여행코스는 KO에서만, 교통은 외국어에서만 유효`() {
        assertTrue(SpotCategory.TRAVEL_COURSE.isAvailableIn(DomainLocale.KO))
        assertFalse(SpotCategory.TRAVEL_COURSE.isAvailableIn(DomainLocale.EN))
        assertFalse(SpotCategory.TRANSPORT.isAvailableIn(DomainLocale.KO))
        assertTrue(SpotCategory.TRANSPORT.isAvailableIn(DomainLocale.EN))
    }

    @Test
    fun `공통 카테고리는 모든 언어에서 유효`() {
        assertTrue(SpotCategory.SHOPPING.isAvailableIn(DomainLocale.KO))
        assertTrue(SpotCategory.SHOPPING.isAvailableIn(DomainLocale.JA))
    }

    @Test
    fun `Accept-Language 태그를 도메인 언어로 정규화하고 미지원은 EN 폴백`() {
        assertEquals(DomainLocale.KO, DomainLocale.fromLanguageTag("ko-KR"))
        assertEquals(DomainLocale.JA, DomainLocale.fromLanguageTag("ja"))
        assertEquals(DomainLocale.ZH, DomainLocale.fromLanguageTag("zh-Hant"))
        assertEquals(DomainLocale.EN, DomainLocale.fromLanguageTag("en-US"))
        assertEquals(DomainLocale.EN, DomainLocale.fromLanguageTag(null)) // AC-11
        assertEquals(DomainLocale.EN, DomainLocale.fromLanguageTag("fr")) // 미지원 → 폴백
    }
}
