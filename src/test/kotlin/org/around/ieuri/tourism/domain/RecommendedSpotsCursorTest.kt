package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.RecommendedSpotsConditions
import org.around.ieuri.tourism.domain.vo.RecommendedSpotsCursor
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** SPEC-0035 R4 — 자립형 커서의 인코딩·만료와 조건 해시. */
class RecommendedSpotsCursorTest {

    private val issuedAt = Instant.parse("2026-09-11T00:00:00Z")

    @Test
    fun `커서는 왕복해도 값이 보존된다`() {
        val original = RecommendedSpotsCursor(conditionsHash = "abc123", position = 100, issuedAt = issuedAt)
        assertEquals(original, RecommendedSpotsCursor.decode(original.encode()))
    }

    @Test
    fun `형식이 깨진 커서는 거부된다 - AC-4_3`() {
        assertFailsWith<IllegalArgumentException> { RecommendedSpotsCursor.decode("not-a-cursor") }
        assertFailsWith<IllegalArgumentException> { RecommendedSpotsCursor.decode("") }
        // 다른 종류의 커서(거리순 `d:` 접두)도 거부된다.
        assertFailsWith<IllegalArgumentException> {
            RecommendedSpotsCursor.decode(org.around.ieuri.tourism.domain.vo.DistanceCursor(10.0, 1L).encode())
        }
    }

    @Test
    fun `수명은 발급 시각 기준이다 - AC-4_4`() {
        val cursor = RecommendedSpotsCursor("abc123", 0, issuedAt)
        assertFalse(cursor.isExpired(issuedAt.plus(Duration.ofHours(23)), Duration.ofHours(24)))
        assertTrue(cursor.isExpired(issuedAt.plus(Duration.ofHours(25)), Duration.ofHours(24)))
    }

    @Test
    fun `조건 해시는 좌표·반경·카테고리·언어·호출자에 묶인다 - AC-4_2`() {
        val base = conditions()
        assertEquals(base.hash, conditions().hash)
        assertNotEquals(base.hash, conditions(radius = 10_000).hash)
        assertNotEquals(base.hash, conditions(language = DomainLocale.JA).hash)
        assertNotEquals(base.hash, conditions(memberId = 99L).hash)
        assertNotEquals(base.hash, conditions(categories = listOf("SHOPPING")).hash)
    }

    @Test
    fun `카테고리 순서는 해시에 영향을 주지 않는다`() {
        assertEquals(
            conditions(categories = listOf("SHOPPING", "RESTAURANT")).hash,
            conditions(categories = listOf("RESTAURANT", "SHOPPING")).hash,
        )
    }

    @Test
    fun `좌표는 소수 5자리로 반올림된다`() {
        assertEquals(conditions(lat = 37.481234).hash, conditions(lat = 37.4812341).hash)
        assertNotEquals(conditions(lat = 37.481234).hash, conditions(lat = 37.48124).hash)
    }

    @Test
    fun `조건의 toString은 좌표를 노출하지 않는다`() {
        assertFalse(conditions(lat = 37.481234).toString().contains("37.48"))
    }

    private fun conditions(
        lat: Double = 37.481234,
        radius: Int = 20_000,
        categories: List<String>? = null,
        language: DomainLocale = DomainLocale.EN,
        memberId: Long? = null,
    ) = RecommendedSpotsConditions(
        center = Coordinates(lat, 126.882),
        radiusMeters = radius,
        categories = categories?.let { CategorySelection.ofValues(it.map(::SpotCategory).toSet()) }
            ?: CategorySelection.UNFILTERED,
        language = language,
        memberId = memberId,
    )
}
