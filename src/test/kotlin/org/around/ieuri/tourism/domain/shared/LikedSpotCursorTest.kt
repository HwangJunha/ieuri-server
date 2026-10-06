package org.around.ieuri.tourism.domain.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

/** 좋아요 목록 커서의 버전 접두사(SPEC-0019). 구 커서는 형식이 같아 접두사로만 거를 수 있다. */
class LikedSpotCursorTest {

    private val updatedAt = Instant.parse("2026-08-14T01:02:03.123456789Z")

    @Test
    @DisplayName("인코딩한 커서를 그대로 되돌린다")
    fun `round trips`() {
        val cursor = LikedSpotCursor(updatedAt = updatedAt, catalogId = 4321)

        val decoded = LikedSpotCursor.decode(cursor.encode())

        assertEquals(updatedAt, decoded.updatedAt)
        assertEquals(4321L, decoded.catalogId)
    }

    @Test
    @DisplayName("나노초까지 보존한다")
    fun `preserves nanosecond precision`() {
        // 잘라내면 같은 마이크로초의 두 행에서 커서가 어긋나 항목을 건너뛴다.
        val decoded = LikedSpotCursor.decode(LikedSpotCursor(updatedAt, 1).encode())

        assertEquals(123456789, decoded.updatedAt.nano)
    }

    @Test
    @DisplayName("v2 접두사가 없는 구 커서는 값 해석 전에 거부된다")
    fun `rejects prefixless legacy cursor`() {
        // 이관 이전 형식: {updatedAt}|{spotId}. 필드 타입이 새 형식과 같다.
        val legacy = encodeRaw("$updatedAt|4321")

        assertThrows(IllegalArgumentException::class.java) { LikedSpotCursor.decode(legacy) }
    }

    @Test
    @DisplayName("다른 접두사도 거부한다")
    fun `rejects unknown prefix`() {
        val other = encodeRaw("v3|$updatedAt|4321")

        assertThrows(IllegalArgumentException::class.java) { LikedSpotCursor.decode(other) }
    }

    @Test
    @DisplayName("필드 수가 다르면 거부한다")
    fun `rejects wrong field count`() {
        assertThrows(IllegalArgumentException::class.java) {
            LikedSpotCursor.decode(encodeRaw("v2|$updatedAt|4321|extra"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LikedSpotCursor.decode(encodeRaw("v2|$updatedAt"))
        }
    }

    @Test
    @DisplayName("Base64가 아니거나 값이 깨진 커서는 거부한다")
    fun `rejects malformed cursor`() {
        assertThrows(IllegalArgumentException::class.java) { LikedSpotCursor.decode("!!!not-base64!!!") }
        assertThrows(IllegalArgumentException::class.java) {
            LikedSpotCursor.decode(encodeRaw("v2|not-an-instant|4321"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LikedSpotCursor.decode(encodeRaw("v2|$updatedAt|not-a-number"))
        }
    }

    private fun encodeRaw(raw: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
}
