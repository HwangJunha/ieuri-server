package org.around.ieuri.community.infrastructure

import org.around.ieuri.community.adapter.persistence.query.decodeCursor
import org.around.ieuri.community.adapter.persistence.query.encodeCursor
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 커서 인코딩·디코딩. */
class CommunityCursorTest {

    @Test
    fun `인코딩한 커서는 원래 정렬 키로 복원된다`() {
        val createdAt = Instant.parse("2026-08-05T01:02:03.123456789Z")
        val decoded = decodeCursor(encodeCursor(createdAt, 42L))

        assertEquals(createdAt, decoded.createdAt)
        assertEquals(42L, decoded.id)
    }

    @Test
    fun `나노초가 보존된다 - 잘라내면 같은 마이크로초의 두 행에서 항목을 건너뛴다`() {
        val createdAt = Instant.parse("2026-08-05T00:00:00.000000001Z")
        assertEquals(createdAt, decodeCursor(encodeCursor(createdAt, 1L)).createdAt)
    }

    @Test
    fun `커서는 정렬 키가 그대로 드러나지 않게 감싼다`() {
        val cursor = encodeCursor(Instant.parse("2026-08-05T00:00:00Z"), 42L)
        // 커서는 평문이 아니다.
        assertEquals(false, cursor.contains("2026"))
        assertEquals(false, cursor.contains("|"))
    }

    @Test
    fun `조작된 커서는 거부된다`() {
        assertFailsWith<IllegalArgumentException> { decodeCursor("!!!not-base64!!!") }
        assertFailsWith<IllegalArgumentException> { decodeCursor("") }
    }

    @Test
    fun `형식이 어긋난 커서는 거부된다`() {
        val malformed = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("구분자없음".toByteArray())
        assertFailsWith<IllegalArgumentException> { decodeCursor(malformed) }
    }
}
