package org.around.ieuri.tourism.domain.spot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** 사라진 원본 정리 게이트(SPEC-0004 AC-14) — 집합을 믿을 수 없으면 지우지 않는다. */
class SpotHarvestTest {

    @Test
    @DisplayName("처음부터 끝까지 본 실행의 수집 집합이 생존 집합이다")
    fun `a complete run yields its collected ids`() {
        assertEquals(setOf("a", "b"), SpotHarvest(setOf("a", "b"), resumed = false).survivors())
    }

    @Test
    @DisplayName("재개 실행은 앞 페이지를 보지 않아 정리를 막는다")
    fun `a resumed run blocks the sweep`() {
        val error = assertThrows<SweepBlockedException> { SpotHarvest(setOf("a"), resumed = true).survivors() }

        assertEquals("resumed run (incomplete id set)", error.reason)
    }

    @Test
    @DisplayName("0건이면 상류 일시 오류일 수 있어 정리를 막는다")
    fun `an empty harvest blocks the sweep`() {
        val error = assertThrows<SweepBlockedException> { SpotHarvest(emptySet(), resumed = false).survivors() }

        assertEquals("collected 0 items", error.reason)
    }
}
