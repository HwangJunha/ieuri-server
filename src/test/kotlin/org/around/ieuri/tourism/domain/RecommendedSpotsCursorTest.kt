package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.DistanceCursor
import org.around.ieuri.tourism.domain.vo.RecommendedSpotsCursor
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SPEC-0035 R4 — 추천 목록 커서의 인코딩·만료. */
class RecommendedSpotsCursorTest {

    private val issuedAt = Instant.parse("2026-09-11T00:00:00Z")

    @Test
    fun `커서는 왕복해도 값이 보존된다`() {
        val original = RecommendedSpotsCursor(position = 100, issuedAt = issuedAt)
        assertEquals(original, RecommendedSpotsCursor.decode(original.encode()))
    }

    @Test
    fun `형식이 깨진 커서와 다른 종류의 커서는 거부된다 - AC-4_3`() {
        assertFailsWith<IllegalArgumentException> { RecommendedSpotsCursor.decode("not-a-cursor") }
        assertFailsWith<IllegalArgumentException> { RecommendedSpotsCursor.decode("") }
        assertFailsWith<IllegalArgumentException> { RecommendedSpotsCursor.decode(DistanceCursor(10.0, 1L).encode()) }
    }

    @Test
    fun `수명은 발급 시각 기준이다 - AC-4_4`() {
        val cursor = RecommendedSpotsCursor(0, issuedAt)
        assertFalse(cursor.isExpired(issuedAt.plus(Duration.ofHours(23)), Duration.ofHours(24)))
        assertTrue(cursor.isExpired(issuedAt.plus(Duration.ofHours(25)), Duration.ofHours(24)))
    }

    @Test
    fun `위치는 음수일 수 없다`() {
        assertFailsWith<IllegalArgumentException> { RecommendedSpotsCursor(-1, issuedAt) }
    }
}
