package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.SpotCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 카테고리 선택 정규화(SPEC-0020 R5·R6). */
class CategorySelectionTest {

    private val boom = { IllegalStateException("EMPTY_CATEGORY_SET") }

    @Test
    fun `생략하면 필터가 없다`() {
        val selection = CategorySelection.of(categories = null, onEmpty = boom)

        assertTrue(selection.isUnfiltered)
        assertEquals(emptyList(), selection.codes())
    }

    @Test
    fun `categories가 오면 그 값들이 선택된다`() {
        val selection = CategorySelection.of(
            categories = listOf(SpotCategory.TOURIST_SPOT, SpotCategory.FESTIVAL_EVENT),
            onEmpty = boom,
        )

        assertEquals(listOf("FESTIVAL_EVENT", "TOURIST_SPOT"), selection.codes())
    }

    /** 값이 하나여도 선택은 집합이다. */
    @Test
    fun `값이 하나면 원소 1개인 집합이 된다`() {
        val selection = CategorySelection.of(
            categories = listOf(SpotCategory("RESTAURANT")),
            onEmpty = boom,
        )

        assertEquals(listOf("RESTAURANT"), selection.codes())
    }

    /** AC6.1 — 빈 배열은 경계에서 거절한다. */
    @Test
    fun `빈 배열은 거절된다`() {
        assertFailsWith<IllegalStateException> {
            CategorySelection.of(categories = emptyList(), onEmpty = boom)
        }
    }

    @Test
    fun `중복은 한 번만 남는다`() {
        val selection = CategorySelection.of(
            categories = listOf(SpotCategory("SHOPPING"), SpotCategory("SHOPPING")),
            onEmpty = boom,
        )

        assertEquals(listOf("SHOPPING"), selection.codes())
    }

    /** 입력 순서와 무관하게 사전순으로 고정된다. */
    @Test
    fun `코드 목록은 입력 순서가 아니라 사전순이다`() {
        val selection = CategorySelection.of(
            categories = listOf(SpotCategory("RESTAURANT"), SpotCategory.TOURIST_SPOT, SpotCategory("LODGING")),
            onEmpty = boom,
        )

        assertEquals(listOf("LODGING", "RESTAURANT", "TOURIST_SPOT"), selection.codes())
    }
}
