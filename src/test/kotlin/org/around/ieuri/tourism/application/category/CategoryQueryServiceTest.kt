package org.around.ieuri.tourism.application.category

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.common.context.AppKey
import org.around.ieuri.tourism.application.category.provided.dto.CategoryDisplay
import org.around.ieuri.tourism.application.category.required.AppCategoryQueryPort
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** CategoryQueryService — 앱별 카테고리 구성과 표시 정보 폴백. */
class CategoryQueryServiceTest {

    private val appCategoryQueryPort = mockk<AppCategoryQueryPort>()
    private val service = CategoryQueryService(appCategoryQueryPort, mockk())

    /** 포트가 준 목록을 가공하지 않는다. 순서는 계약이 아니므로 집합으로 본다. */
    @Test
    fun `availableCategories는 앱과 언어로 구성을 조회해 그대로 돌려준다`() {
        val appKey = AppKey("demo-app")
        every { appCategoryQueryPort.findCategories(appKey, DomainLocale.KO) } returns listOf(
            CategoryDisplay(SpotCategory("RESTAURANT"), "음식점", "https://cdn/restaurant.png"),
            CategoryDisplay(SpotCategory("TRAVEL_COURSE"), "여행코스", null),
        )

        val result = service.availableCategories(appKey, DomainLocale.KO)

        assertEquals(setOf(SpotCategory("RESTAURANT"), SpotCategory("TRAVEL_COURSE")), result.map { it.category }.toSet())
        assertEquals(mapOf("RESTAURANT" to "음식점", "TRAVEL_COURSE" to "여행코스"), result.associate { it.category.code to it.label })
        assertEquals(
            mapOf("RESTAURANT" to "https://cdn/restaurant.png", "TRAVEL_COURSE" to null),
            result.associate { it.category.code to it.iconUrl },
        )
        verify(exactly = 1) { appCategoryQueryPort.findCategories(appKey, DomainLocale.KO) }
    }

    /** 정의가 없는 코드는 코드 자체를 라벨로 채운다. */
    @Test
    fun `categoryDisplays는 정의가 없는 코드를 폴백으로 채운다`() {
        every { appCategoryQueryPort.findDisplays(setOf("RESTAURANT", "BRAND_NEW"), DomainLocale.KO) } returns
            mapOf("RESTAURANT" to CategoryDisplay(SpotCategory("RESTAURANT"), "음식점", "https://cdn/restaurant.png"))

        val result = service.categoryDisplays(setOf("RESTAURANT", "BRAND_NEW"), DomainLocale.KO)

        assertEquals(setOf("RESTAURANT", "BRAND_NEW"), result.keys)
        assertEquals("음식점", result["RESTAURANT"]?.label)
        assertEquals("https://cdn/restaurant.png", result["RESTAURANT"]?.iconUrl)
        assertEquals("BRAND_NEW", result["BRAND_NEW"]?.label)
        assertNull(result["BRAND_NEW"]?.iconUrl)
    }

    /** 다른 도메인이 쓰는 문자열 경로. */
    @Test
    fun `availableCategoryCodes는 문자열 축으로 같은 구성을 돌려준다`() {
        val appKey = AppKey("k-trip-spot")
        every { appCategoryQueryPort.findCategories(appKey, DomainLocale.JA) } returns
            listOf(CategoryDisplay(SpotCategory("TRANSPORT"), "交通", "https://cdn/transport.png"))

        val result = service.availableCategoryCodes(appKey, "JA")

        assertEquals(listOf("TRANSPORT"), result.map { it.category })
        assertEquals(listOf("交通"), result.map { it.label })
        assertEquals(listOf("https://cdn/transport.png"), result.map { it.iconUrl })
    }
}
