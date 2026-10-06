package org.around.ieuri.tourism.domain.visit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DistrictVisitsTest {

    @Test
    fun `빈 코드는 버리고 중복은 처음 나온 순서로 하나만 남긴다`() {
        val visits = DistrictVisits.of(7L, listOf("41111", " ", "11110", "41111", "", "11110"))

        assertEquals(listOf("41111", "11110"), visits.districtCodes)
        assertEquals(7L, visits.memberId)
    }

    @Test
    fun `빈 코드뿐이면 비어 있다`() {
        assertTrue(DistrictVisits.of(7L, listOf(" ", "")).isEmpty)
        assertFalse(DistrictVisits.of(7L, listOf("11110")).isEmpty)
    }

    @Test
    fun `있는 시군구만 남기고 순서는 유지한다`() {
        val visits = DistrictVisits.of(7L, listOf("41111", "99999", "11110"))

        assertEquals(listOf("41111", "11110"), visits.keepOnly(setOf("11110", "41111")).districtCodes)
    }

    @Test
    fun `회원 id는 양수여야 한다`() {
        assertFailsWith<IllegalArgumentException> { DistrictVisits.of(0L, listOf("11110")) }
    }
}
