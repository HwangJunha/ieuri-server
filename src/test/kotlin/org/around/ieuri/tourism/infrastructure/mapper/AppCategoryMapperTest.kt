package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.AppCategoryEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `app_category` 엔티티 + 표시 정보 → read DTO(SPEC-0020). 필드별로 값을 달리해 뒤바뀜을 잡는다. */
class AppCategoryMapperTest {

    @Test
    fun `엔티티와 표시 정보의 각 필드가 제자리로 옮겨진다`() {
        val entity = AppCategoryEntity(
            id = 7L,
            appKey = "k-trip-spot",
            language = DomainLocale.JA,
            category = "SHOPPING",
            position = 3,
            enabled = true,
        )
        val display = CategoryDisplay(
            category = SpotCategory("SHOPPING"),
            label = "ショッピング",
            iconUrl = "https://cdn.example.com/shopping.png",
        )

        val view = entity.toView(display)

        assertEquals(SpotCategory("SHOPPING"), view.category)
        assertEquals("ショッピング", view.label)
        assertEquals("https://cdn.example.com/shopping.png", view.iconUrl)
        assertEquals(3, view.position)
    }

    /** 표시 정보가 없으면 코드가 라벨이 되고 아이콘은 null이다 — 빈 칩보다 낫다. */
    @Test
    fun `표시 정의가 없으면 코드를 라벨로 쓰는 폴백이 된다`() {
        val entity = AppCategoryEntity(
            id = 11L,
            appKey = "demo-app",
            language = DomainLocale.KO,
            category = "BRAND_NEW",
            position = 5,
            enabled = true,
        )

        val view = entity.toView(display = null)

        assertEquals(SpotCategory("BRAND_NEW"), view.category)
        assertEquals("BRAND_NEW", view.label)
        assertNull(view.iconUrl)
        assertEquals(5, view.position)
    }

    /** 아이콘만 비어 있는 경우도 라벨은 살아 있어야 한다. */
    @Test
    fun `아이콘이 없는 표시 정보도 라벨은 옮겨진다`() {
        val entity = AppCategoryEntity(
            id = 2L,
            appKey = "k-trip-spot",
            language = DomainLocale.EN,
            category = "TOURIST_SPOT",
            position = 1,
            enabled = true,
        )
        val display = CategoryDisplay(
            category = SpotCategory.TOURIST_SPOT,
            label = "Attractions",
            iconUrl = null,
        )

        val view = entity.toView(display)

        assertEquals(SpotCategory.TOURIST_SPOT, view.category)
        assertEquals("Attractions", view.label)
        assertNull(view.iconUrl)
        assertEquals(1, view.position)
    }

    /** position은 Short 컬럼이지만 DTO는 Int다 — 변환에서 값이 바뀌지 않아야 한다. */
    @Test
    fun `0번 자리도 그대로 옮겨진다`() {
        val entity = AppCategoryEntity(
            id = 1L,
            appKey = "demo-app",
            language = DomainLocale.KO,
            category = "TOURIST_SPOT",
            position = 0,
            enabled = true,
        )

        val view = entity.toView(CategoryDisplay.fallback(SpotCategory.TOURIST_SPOT))

        assertEquals(SpotCategory.TOURIST_SPOT, view.category)
        assertEquals(0, view.position)
    }
}
