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
 *
 * 정렬이 `resolved ASC, createdAt DESC`라 **커서가 정렬 키 셋을 전부 담아야 한다** —
 * 시각만 담으면 미해결 구간과 해결 구간의 시각이 겹쳐 경계에서 항목이 중복되거나 누락된다.
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

    /** 나노초를 잘라내면 같은 마이크로초의 두 행에서 커서가 어긋나 항목을 건너뛴다. */
    @Test
    fun `나노초까지 보존된다`() {
        val decoded = decodeSpotQuestionCursor(
            encodeSpotQuestionCursor(resolved = false, createdAt = createdAt, id = 1L),
        )

        assertEquals(123456789, decoded.createdAt.nano)
    }

    /** 해결 여부가 커서에 실려야 경계에서 구간을 구분할 수 있다. */
    @Test
    fun `해결 여부가 다르면 커서 값도 다르다`() {
        val resolved = encodeSpotQuestionCursor(resolved = true, createdAt = createdAt, id = 42L)
        val unresolved = encodeSpotQuestionCursor(resolved = false, createdAt = createdAt, id = 42L)

        assert(resolved != unresolved)
    }

    @Test
    fun `다른 커넥션의 커서는 거부된다`() {
        // 접두어가 없는 커뮤니티 커서를 그대로 넘기면 조용히 엉뚱한 위치로 가는 대신 오류다.
        assertFailsWith<IllegalArgumentException> {
            decodeSpotQuestionCursor(encodeCursor(createdAt, 42L))
        }
    }

    @Test
    fun `조작된 커서는 거부된다`() {
        assertFailsWith<IllegalArgumentException> { decodeSpotQuestionCursor("!!not-base64!!") }
    }
}
