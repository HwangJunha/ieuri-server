package org.around.ieuri.community.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorToken
import java.time.Instant

/**
 * 거리순 피드의 keyset 커서. 정렬 키 (거리, createdAt, id)를 담는다.
 * - `d:` 접두어로 최신순 커서와 구분한다 — 섞어 넘기면 오류가 난다.
 */
data class DistanceCursor(val distanceMeters: Double, val createdAt: Instant, val id: Long)

fun encodeDistanceCursor(distanceMeters: Double, createdAt: Instant, id: Long): String = CursorToken.wrap("d:$distanceMeters|$createdAt|$id")

fun decodeDistanceCursor(cursor: String): DistanceCursor = try {
    val decoded = CursorToken.unwrap(cursor)
    require(decoded.startsWith("d:")) { "거리순 커서가 아닙니다." }
    val (distance, instant, id) = decoded.removePrefix("d:").split("|", limit = 3)
    DistanceCursor(
        distanceMeters = distance.toDouble(),
        createdAt = Instant.parse(instant),
        id = id.toLong(),
    )
} catch (e: RuntimeException) {
    throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
}
