package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.FeedConditions
import org.around.ieuri.tourism.domain.vo.FeedCursor
import org.around.ieuri.tourism.domain.vo.FeedCursorSecret
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** SPEC-0035 R4 — 자립형 서명 커서의 인코딩·서명·만료와 조건 해시. */
class FeedCursorTest {

    private val secret = FeedCursorSecret("test-secret")
    private val issuedAt = Instant.parse("2026-09-11T00:00:00Z")

    @Test
    fun `커서는 왕복해도 값이 보존된다`() {
        val original = FeedCursor(conditionsHash = "abc123", position = 100, issuedAt = issuedAt)
        assertEquals(original, FeedCursor.decode(original.encode(secret), secret))
    }

    @Test
    fun `서명이 맞지 않으면 거부된다 - AC-4_3`() {
        val encoded = FeedCursor("abc123", 100, issuedAt).encode(secret)
        assertFailsWith<IllegalArgumentException> { FeedCursor.decode(encoded, FeedCursorSecret("other-secret")) }
    }

    @Test
    fun `조건 해시를 바꿔치기하면 서명이 어긋나 거부된다 - AC-4_3`() {
        val encoded = FeedCursor("abc123", 100, issuedAt).encode(secret)
        val tampered = String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
            .replace("abc123", "zzz999")
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(StandardCharsets.UTF_8)) }
        assertFailsWith<IllegalArgumentException> { FeedCursor.decode(tampered, secret) }
    }

    @Test
    fun `형식이 깨진 커서는 거부된다 - AC-4_3`() {
        assertFailsWith<IllegalArgumentException> { FeedCursor.decode("not-a-cursor", secret) }
        assertFailsWith<IllegalArgumentException> { FeedCursor.decode("", secret) }
    }

    @Test
    fun `수명은 발급 시각 기준이다 - AC-4_4`() {
        val cursor = FeedCursor("abc123", 0, issuedAt)
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
    fun `조건과 커서의 toString은 좌표를 노출하지 않는다`() {
        val text = conditions(lat = 37.481234).toString()
        assertFalse(text.contains("37.48"))
    }

    private fun conditions(
        lat: Double = 37.481234,
        radius: Int = 20_000,
        categories: List<String>? = null,
        language: DomainLocale = DomainLocale.EN,
        memberId: Long? = null,
    ) = FeedConditions(
        center = Coordinates(lat, 126.882),
        radiusMeters = radius,
        categories = categories?.let { CategorySelection.ofValues(it.map(::SpotCategory).toSet()) }
            ?: CategorySelection.UNFILTERED,
        language = language,
        memberId = memberId,
    )
}
