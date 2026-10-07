package org.around.ieuri.common.pagination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OffsetPage — 다음 페이지 여부를 전체 건수에서 도출한다. */
class OffsetPageTest {

    @Test
    fun `전체 건수가 지금까지 넘긴 건수보다 많으면 다음 페이지가 있다`() {
        assertTrue(OffsetPage.of(listOf("a"), page = 2, size = 10, totalCount = 21).hasNext)
    }

    @Test
    fun `마지막 페이지 경계에서는 다음 페이지가 없다`() {
        assertFalse(OffsetPage.of(listOf("a"), page = 2, size = 10, totalCount = 20).hasNext)
    }

    @Test
    fun `큰 페이지 번호도 넘치지 않고 판정한다`() {
        assertFalse(OffsetPage.of(emptyList<String>(), page = Int.MAX_VALUE, size = Int.MAX_VALUE, totalCount = Int.MAX_VALUE).hasNext)
    }

    @Test
    fun `빈 페이지는 요청한 번호를 유지하고 건수는 0이다`() {
        val page = OffsetPage.empty<String>(3)

        assertEquals(3, page.page)
        assertEquals(emptyList(), page.items)
        assertFalse(page.hasNext)
        assertEquals(0, page.totalCount)
    }
}
