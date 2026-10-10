package org.around.ieuri.community.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorToken
import java.time.Instant

/**
 * 질문 목록의 keyset 커서. 정렬 키 (resolved, createdAt, id)를 전부 담는다.
 * - `q:` 접두어로 [CommunityCursor]와 구분한다 — 섞어 넘기면 오류가 난다.
 * - 시각은 ISO-8601로 담아 나노초까지 보존한다.
 */
data class SpotQuestionCursor(
    val resolved: Boolean,
    val createdAt: Instant,
    val id: Long,
)

fun encodeSpotQuestionCursor(resolved: Boolean, createdAt: Instant, id: Long): String = CursorToken.wrap("q:$resolved|$createdAt|$id")

/** 조작·구버전 커서는 첫 페이지로 되돌리지 않고 [IllegalArgumentException]으로 거부한다. */
fun decodeSpotQuestionCursor(cursor: String): SpotQuestionCursor = try {
    val decoded = CursorToken.unwrap(cursor)
    require(decoded.startsWith("q:")) { "질문 목록 커서가 아닙니다." }
    val (resolved, instant, id) = decoded.removePrefix("q:").split("|", limit = 3)
    SpotQuestionCursor(
        resolved = resolved.toBooleanStrict(),
        createdAt = Instant.parse(instant),
        id = id.toLong(),
    )
} catch (e: RuntimeException) {
    throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
}
