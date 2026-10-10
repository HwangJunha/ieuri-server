package org.around.ieuri.tourism.domain.footprint

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class FootprintTest {

    private val known = setOf("11110", "41111", "41113")
    private val issued = ShareLink("already-issued", Instant.parse("2026-08-01T00:00:00Z"))

    private fun footprint(visited: Set<String> = emptySet(), link: ShareLink? = null) = Footprint(7, visited, link)

    @Test
    @DisplayName("이미 방문한 코드는 새로 남기지 않는다 — 오류도 아니다")
    fun `already visited codes are not added`() {
        val visit = footprint(visited = setOf("11110")).visit(listOf("11110", "41111"), known)

        assertEquals(listOf("41111"), visit.addedDistrictCodes)
        assertEquals(setOf("11110", "41111"), visit.footprint.visitedDistrictCodes)
        assertEquals(emptyList<String>(), visit.unknownDistrictCodes)
    }

    @Test
    @DisplayName("없는 시군구 코드는 남기지 않고 따로 돌려준다")
    fun `unknown codes are reported, not recorded`() {
        val visit = footprint().visit(listOf("11110", "99999"), known)

        assertEquals(listOf("11110"), visit.addedDistrictCodes)
        assertEquals(listOf("99999"), visit.unknownDistrictCodes)
    }

    @Test
    @DisplayName("빈 코드는 버리고 중복은 처음 나온 순서로 하나만 남긴다")
    fun `blank codes are dropped and duplicates folded`() {
        val visit = footprint().visit(listOf(" ", "41113", "", "11110", "41113"), known)

        assertEquals(listOf("41113", "11110"), visit.addedDistrictCodes)
    }

    @Test
    @DisplayName("방문 해제는 방문한 코드만 지운다")
    fun `unvisit removes only visited codes`() {
        val unvisit = footprint(visited = setOf("41111", "11110")).unvisit(listOf("41111", "41113"))

        assertEquals(setOf("41111"), unvisit.removedDistrictCodes)
        assertEquals(setOf("11110"), unvisit.footprint.visitedDistrictCodes)
    }

    @Test
    @DisplayName("유효한 링크가 있으면 공유는 같은 링크다")
    fun `sharing again returns the active link`() {
        assertSame(issued, footprint(link = issued).share())
    }

    @Test
    @DisplayName("링크가 없으면 저장되지 않은 새 링크를 발급한다")
    fun `sharing without a link issues a fresh one`() {
        val first = footprint().share()
        val second = footprint().share()

        assertNull(first.issuedAt)
        assertNotEquals(first.token, second.token)
    }

    @Test
    @DisplayName("지금 유효한 링크의 토큰이어야 거둘 수 있다")
    fun `only the active token can be revoked`() {
        assertSame(issued, footprint(link = issued).revokeShare("already-issued"))
        assertNull(footprint(link = issued).revokeShare("other"))
        assertNull(footprint().revokeShare("already-issued"))
    }

    @Test
    @DisplayName("회원 id는 양수다")
    fun `member id must be positive`() {
        assertThrows<IllegalArgumentException> { Footprint(0, emptySet(), null) }
    }
}
