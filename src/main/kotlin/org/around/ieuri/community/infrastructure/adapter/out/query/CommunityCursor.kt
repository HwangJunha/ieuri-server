package org.around.ieuri.community.infrastructure.adapter.out.query

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

/**
 * 커뮤니티 커넥션의 keyset 커서. 정렬 키 (createdAt, id)를 Base64로 감싼 불투명 값이다.
 * - 시각은 ISO-8601로 담아 나노초까지 보존한다(잘라내면 항목을 건너뛴다).
 */
data class CommunityCursor(val createdAt: Instant, val id: Long)

fun encodeCursor(createdAt: Instant, id: Long): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString("$createdAt|$id".toByteArray(StandardCharsets.UTF_8))

/** 조작·구버전 커서는 첫 페이지로 되돌리지 않고 [IllegalArgumentException]으로 거부한다. */
fun decodeCursor(cursor: String): CommunityCursor = try {
    val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
    val (instant, id) = decoded.split("|", limit = 2)
    CommunityCursor(createdAt = Instant.parse(instant), id = id.toLong())
} catch (e: RuntimeException) {
    throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
}

/**
 * 거리순 피드의 keyset 커서. 정렬 키 (거리, createdAt, id)를 담는다.
 * - `d:` 접두어로 최신순 커서와 구분한다 — 섞어 넘기면 오류가 난다.
 */
data class DistanceCursor(val distanceMeters: Double, val createdAt: Instant, val id: Long)

fun encodeDistanceCursor(distanceMeters: Double, createdAt: Instant, id: Long): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString("d:$distanceMeters|$createdAt|$id".toByteArray(StandardCharsets.UTF_8))

fun decodeDistanceCursor(cursor: String): DistanceCursor = try {
    val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
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
