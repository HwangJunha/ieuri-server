package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.application.shared.exception.InvalidCursorException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** SPEC-0013 커서 인코딩·디코딩(축제·인기). 순수 함수라 도메인 단위로 직접 검증한다. */
class KeysetCursorsTest {

    @Test
    fun `거리 커서는 왕복해도 값이 보존된다`() {
        val original = DistanceCursor(distanceMeters = 1234.5678, spotId = 7L)
        assertEquals(original, DistanceCursor.decode(original.encode()))
    }

    @Test
    fun `인기 커서는 왕복해도 값이 보존된다`() {
        val original = LikeCountCursor(likeCount = 99L, spotId = 3L)
        assertEquals(original, LikeCountCursor.decode(original.encode()))
    }

    @Test
    fun `커서는 내용을 그대로 드러내지 않는다`() {
        val encoded = LikeCountCursor(likeCount = 42L, spotId = 77L).encode()
        assertEquals(false, encoded.contains("42"))
        assertEquals(false, encoded.contains("77"))
    }

    @Test
    fun `다른 종류의 커서는 거부된다`() {
        val distance = DistanceCursor(distanceMeters = 100.0, spotId = 1L).encode()
        assertFailsWith<InvalidCursorException> { LikeCountCursor.decode(distance) }
        val like = LikeCountCursor(likeCount = 1L, spotId = 1L).encode()
        assertFailsWith<InvalidCursorException> { DistanceCursor.decode(like) }
    }

    @Test
    fun `조작된 커서는 거부된다`() {
        assertFailsWith<InvalidCursorException> { LikeCountCursor.decode("!!!not-base64!!!") }
        assertFailsWith<InvalidCursorException> { DistanceCursor.decode("") }
    }

    @Test
    fun `관계 커서는 왕복해도 같다`() {
        val original = RelationCursor(
            createdAt = java.time.Instant.parse("2026-08-20T01:02:03Z"),
            counterpartId = 42L,
        )
        assertEquals(original, RelationCursor.decode(original.encode()))
    }

    @Test
    fun `관계 커서는 다른 종류를 거부한다`() {
        val like = LikeCountCursor(likeCount = 1L, spotId = 1L).encode()
        assertFailsWith<InvalidCursorException> { RelationCursor.decode(like) }
    }

    /** 클라이언트가 들고 있는 커서가 계속 통해야 한다 — 바이트 형식을 고정한다. */
    @Test
    fun `커서 바이트 형식은 이전과 같다`() {
        assertEquals(wrap("d:100.5|7"), DistanceCursor(distanceMeters = 100.5, spotId = 7L).encode())
        assertEquals(wrap("l:42|7"), LikeCountCursor(likeCount = 42L, spotId = 7L).encode())
        assertEquals(wrap("r:2026-08-20T01:02:03Z|9"), RelationCursor(java.time.Instant.parse("2026-08-20T01:02:03Z"), 9L).encode())
        assertEquals(wrap("v2|2026-08-20T01:02:03Z|9"), LikedSpotCursor(java.time.Instant.parse("2026-08-20T01:02:03Z"), 9L).encode())
    }

    private fun wrap(raw: String): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
}
