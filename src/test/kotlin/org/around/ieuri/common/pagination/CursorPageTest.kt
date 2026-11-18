package org.around.ieuri.common.pagination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** keysetPageOf — `first + 1`건을 받아 초과분으로 다음 페이지를 판정하고 잘라낸다. */
class CursorPageTest {

    @Test
    fun `요청보다 한 건 더 오면 다음 페이지가 있고 초과분은 잘린다`() {
        val page = keysetPageOf(rows = listOf(1, 2, 3), first = 2, after = null) { "c$it" }

        assertEquals(listOf(1, 2), page.edges.map { it.node })
        assertTrue(page.pageInfo.hasNextPage)
        assertEquals("c1", page.pageInfo.startCursor)
        // 다음 페이지는 잘린 행이 아니라 이 페이지의 마지막 행 뒤에서 시작한다.
        assertEquals("c2", page.pageInfo.endCursor)
    }

    @Test
    fun `요청한 만큼만 오면 마지막 페이지다`() {
        val page = keysetPageOf(rows = listOf(1, 2), first = 2, after = null) { "c$it" }

        assertFalse(page.pageInfo.hasNextPage)
        assertEquals(listOf("c1", "c2"), page.edges.map { it.cursor })
    }

    @Test
    fun `커서를 넘겨 받은 페이지만 앞 페이지가 있다`() {
        assertTrue(keysetPageOf(rows = listOf(1), first = 2, after = "c0") { "c$it" }.pageInfo.hasPreviousPage)
        assertFalse(keysetPageOf(rows = listOf(1), first = 2, after = null) { "c$it" }.pageInfo.hasPreviousPage)
    }

    @Test
    fun `빈 결과는 커서가 없다`() {
        val page = keysetPageOf(rows = emptyList<Int>(), first = 2, after = null) { "c$it" }

        assertTrue(page.edges.isEmpty())
        assertNull(page.pageInfo.startCursor)
        assertNull(page.pageInfo.endCursor)
    }
}
