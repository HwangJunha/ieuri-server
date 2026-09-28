package org.around.ieuri.community.infrastructure

import org.around.ieuri.community.infrastructure.adapter.out.query.decodeSpotQuestionCursor
import org.around.ieuri.community.infrastructure.adapter.out.query.encodeCursor
import org.around.ieuri.community.infrastructure.adapter.out.query.encodeSpotQuestionCursor
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 질문 목록 커서(SPEC-0026 R2, R16).
 * - 정렬이 `resolved ASC, createdAt DESC`라 커서가 정렬 키 셋을 전부 담는다.
 */
class SpotQuestionCursorTest {

    private val createdAt = Instant.parse("2026-08-18T01:02:03.123456789Z")

    @Test
    fun `왕복하면 정렬 키 셋이 그대로 복원된다`() {
        val decoded = decodeSpotQuestionCursor(
            encodeSpotQuestionCursor(resolved = true, createdAt = createdAt, id = 42L),
        )

        assertEquals(true, decoded.resolved)
        assertEquals(createdAt, decoded.createdAt)
        assertEquals(42L, decoded.id)
    }

    @Test
    fun `미해결 커서도 그대로 복원된다`() {
        val decoded = decodeSpotQuestionCursor(
            encodeSpotQuestionCursor(resolved = false, createdAt = createdAt, id = 1L),
        )

        assertEquals(false, decoded.resolved)
    }

    @Test
    fun `나노초까지 보존된다`() {
        val decoded = decodeSpotQuestionCursor(
            encodeSpotQuestionCursor(resolved = false, createdAt = createdAt, id = 1L),
        )

        assertEquals(123456789, decoded.createdAt.nano)
    }

    /** 해결 여부가 커서에 실린다. */
    @Test
    fun `해결 여부가 다르면 커서 값도 다르다`() {
        val resolved = encodeSpotQuestionCursor(resolved = true, createdAt = createdAt, id = 42L)
        val unresolved = encodeSpotQuestionCursor(resolved = false, createdAt = createdAt, id = 42L)

        assert(resolved != unresolved)
    }

    @Test
    fun `다른 커넥션의 커서는 거부된다`() {
        // 접두어가 없는 커뮤니티 커서는 오류다.
        assertFailsWith<IllegalArgumentException> {
            decodeSpotQuestionCursor(encodeCursor(createdAt, 42L))
        }
    }

    @Test
    fun `조작된 커서는 거부된다`() {
        assertFailsWith<IllegalArgumentException> { decodeSpotQuestionCursor("!!not-base64!!") }
    }
}
