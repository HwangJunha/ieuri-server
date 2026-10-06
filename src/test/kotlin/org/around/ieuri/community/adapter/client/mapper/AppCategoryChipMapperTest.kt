package org.around.ieuri.community.adapter.client.mapper

import org.around.ieuri.community.domain.shared.TargetCategory
import org.around.ieuri.tourism.application.shared.provided.dto.AppCategoryCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** tourism 카테고리 구성 → 커뮤니티 칩(SPEC-0020). 이름으로 옮기고 모르는 값은 빠진다. */
class AppCategoryChipMapperTest {

    @Test
    fun `카테고리가 이름으로 옮겨진다`() {
        val source = AppCategoryCode(
            category = "FESTIVAL_EVENT",
            label = "축제·행사",
            iconUrl = "https://cdn.example.com/festival.png",
        )

        val chip = assertNotNull(source.toChipViewOrNull())

        assertEquals(TargetCategory.FESTIVAL_EVENT, chip.category)
    }

    /** 커뮤니티가 아는 값은 모두 옮겨진다. */
    @Test
    fun `커뮤니티가 아는 모든 카테고리가 대응된다`() {
        TargetCategory.entries.forEach { category ->
            val chip = source(category.name).toChipViewOrNull()

            assertNotNull(chip, "$category 가 커뮤니티 칩으로 옮겨지지 않았다")
            assertEquals(category.name, chip.category.name)
        }
    }

    /** tourism이 카테고리를 먼저 늘려도 커뮤니티는 깨지지 않고 그 칩만 빠진다. */
    @Test
    fun `커뮤니티가 모르는 카테고리는 칩에서 빠진다`() {
        assertNull(source("BRAND_NEW_CATEGORY").toChipViewOrNull())
    }

    /** 라벨·아이콘이 비어 있어도 카테고리는 옮겨진다. */
    @Test
    fun `아이콘이 없어도 칩으로 옮겨진다`() {
        val source = AppCategoryCode(category = "LODGING", label = "숙박", iconUrl = null)

        assertEquals(TargetCategory.LODGING, assertNotNull(source.toChipViewOrNull()).category)
    }

    private fun source(category: String) = AppCategoryCode(
        category = category,
        label = "라벨-$category",
        iconUrl = "https://cdn.example.com/$category.png",
    )
}
