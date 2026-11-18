package org.around.ieuri.community.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorToken
import java.time.Instant

/**
 * 커뮤니티 커넥션의 keyset 커서. 정렬 키 (createdAt, id)를 [CursorToken]으로 감싼 불투명 값이다.
 * - 시각은 ISO-8601로 담아 나노초까지 보존한다(잘라내면 항목을 건너뛴다).
 */
data class CommunityCursor(val createdAt: Instant, val id: Long)

fun encodeCursor(createdAt: Instant, id: Long): String = CursorToken.wrap("$createdAt|$id")

/** 조작·구버전 커서는 첫 페이지로 되돌리지 않고 [IllegalArgumentException]으로 거부한다. */
fun decodeCursor(cursor: String): CommunityCursor = try {
    val (instant, id) = CursorToken.unwrap(cursor).split("|", limit = 2)
    CommunityCursor(createdAt = Instant.parse(instant), id = id.toLong())
} catch (e: RuntimeException) {
    throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
}
