package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.common.error.EmptyCategorySetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** GraphQL 인자 → 카테고리 선택(SPEC-0020 R5·R6)의 문자열 경계. 규칙 자체는 CategorySelectionTest가 본다. */
class CategoryArgumentMapperTest {

    @Test
    fun `문자열 인자가 도메인 값으로 옮겨진다`() {
        val selection = toCategorySelection(listOf("TOURIST_SPOT", "RESTAURANT"))

        assertEquals(listOf("RESTAURANT", "TOURIST_SPOT"), selection.codes())
    }

    @Test
    fun `값이 하나여도 선택이 만들어진다`() {
        val selection = toCategorySelection(listOf("LODGING"))

        assertEquals(listOf("LODGING"), selection.codes())
    }

    @Test
    fun `생략하면 필터가 없다`() {
        assertTrue(toCategorySelection(null).isUnfiltered)
    }

    /** 서버가 모르는 코드도 통과한다 — 카테고리는 배포 없이 늘어나는 축이다(SPEC-0020). */
    @Test
    fun `알 수 없는 코드도 거절되지 않는다`() {
        val selection = toCategorySelection(listOf("BRAND_NEW_CATEGORY"))

        assertEquals(listOf("BRAND_NEW_CATEGORY"), selection.codes())
    }

    /** AC6.1 — 빈 배열은 EMPTY_CATEGORY_SET이며, 그 코드로 클라이언트가 분기한다. */
    @Test
    fun `빈 배열은 EMPTY_CATEGORY_SET으로 거절된다`() {
        val thrown = assertFailsWith<EmptyCategorySetException> {
            toCategorySelection(emptyList())
        }

        assertEquals("EMPTY_CATEGORY_SET", thrown.code)
    }
}
