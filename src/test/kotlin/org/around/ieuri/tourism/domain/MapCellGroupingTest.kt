package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.MapCell
import org.around.ieuri.tourism.domain.vo.MapCellDistrictRow
import org.around.ieuri.tourism.domain.vo.MapCellGrouping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 칸 묶음과 방문 판정(SPEC-0030 R6·R7). */
class MapCellGroupingTest {

    @Test
    fun `서울 25개 구는 1칸이 된다`() {
        val rows = (1..25).map { row(cellCode = "11", districtCode = "111%02d".format(it), cellName = "서울특별시") }

        val cells = MapCellGrouping.fold(rows)

        assertEquals(1, cells.size)
        assertEquals("11", cells.single().code)
        assertEquals(25, cells.single().districtCodes.size)
    }

    @Test
    fun `수원시 4개 구는 1칸이 된다`() {
        val rows = listOf("41111", "41113", "41115", "41117")
            .map { row(cellCode = "41111", districtCode = it, cellName = "수원시") }

        val cells = MapCellGrouping.fold(rows)

        assertEquals(1, cells.size)
        assertEquals("41111", cells.single().code)
        assertEquals(setOf("41111", "41113", "41115", "41117"), cells.single().districtCodes)
    }

    @Test
    fun `구가 없는 시군은 시군구 하나가 그대로 1칸이다`() {
        val cells = MapCellGrouping.fold(listOf(row(cellCode = "41210", districtCode = "41210", cellName = "광명시")))

        assertEquals(1, cells.size)
        assertEquals(setOf("41210"), cells.single().districtCodes)
    }

    @Test
    fun `칸 이름은 시군구 이름이 아니라 칸 이름이다`() {
        val rows = listOf(
            row(cellCode = "11", districtCode = "11110", cellName = "서울특별시"),
            row(cellCode = "11", districtCode = "11140", cellName = "서울특별시"),
        )

        assertEquals("서울특별시", MapCellGrouping.fold(rows).single().name.ko)
    }

    @Test
    fun `정렬은 시도 코드 다음 칸 코드 오름차순이다`() {
        val rows = listOf(
            row(cellCode = "41210", districtCode = "41210", provinceCode = "41"),
            row(cellCode = "11", districtCode = "11110", provinceCode = "11"),
            row(cellCode = "41111", districtCode = "41111", provinceCode = "41"),
        )

        assertEquals(listOf("11", "41111", "41210"), MapCellGrouping.fold(rows).map { it.code })
    }

    @Test
    fun `칸이 없으면 빈 목록이다`() {
        assertEquals(emptyList(), MapCellGrouping.fold(emptyList()))
    }

    @Test
    fun `칸에 속한 시군구 중 하나라도 방문이면 칸이 방문이다`() {
        val cell = MapCellGrouping.fold(
            listOf("41111", "41113", "41115", "41117").map { row(cellCode = "41111", districtCode = it) },
        ).single()

        // 장안구 하나만 방문해도 수원시 칸 전체가 방문이다(R6).
        assertTrue(cell.isVisitedBy(setOf("41111")))
    }

    @Test
    fun `칸에 속한 시군구를 하나도 방문하지 않으면 미방문이다`() {
        val cell = MapCellGrouping.fold(
            listOf("41111", "41113").map { row(cellCode = "41111", districtCode = it) },
        ).single()

        assertFalse(cell.isVisitedBy(setOf("11110", "41210")))
        assertFalse(cell.isVisitedBy(emptySet()))
    }

    @Test
    fun `시군구가 없는 칸은 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            MapCell(
                code = "11",
                districtCodes = emptySet(),
                name = AreaName("서울", "Seoul", null, null),
                provinceName = AreaName("서울", "Seoul", null, null),
                provinceCode = "11",
                center = Coordinates(37.5, 127.0),
            )
        }
    }

    private fun row(
        cellCode: String,
        districtCode: String,
        cellName: String = "칸",
        provinceCode: String = cellCode.take(2),
    ) = MapCellDistrictRow(
        cellCode = cellCode,
        districtCode = districtCode,
        cellName = AreaName(ko = cellName, en = "cell", ja = null, zh = null),
        provinceName = AreaName(ko = "시도", en = "province", ja = null, zh = null),
        provinceCode = provinceCode,
        center = Coordinates(latitude = 37.5, longitude = 127.0),
    )
}
