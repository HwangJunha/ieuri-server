package org.around.ieuri.community.infrastructure.adapter.out.query

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

/**
 * 커뮤니티 커넥션의 keyset 커서. 정렬 키 (createdAt, id)를 Base64로 감싼 값이다.
 *
 * 불투명하게 감싸는 목적은 값을 숨기는 것이 아니라 클라이언트가 커서를 조립하지 못하게
 * 막는 것이다 — 형식이 드러나면 정렬 키를 바꾸는 순간 파괴적 변경이 된다.
 *
 * 시각은 ISO-8601로 담아 나노초까지 보존한다. 잘라내면 같은 마이크로초의 두 행에서 커서가
 * 어긋나 항목을 건너뛴다.
 */
data class CommunityCursor(val createdAt: Instant, val id: Long)

fun encodeCursor(createdAt: Instant, id: Long): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString("$createdAt|$id".toByteArray(StandardCharsets.UTF_8))

/** 조작·구버전 커서는 [IllegalArgumentException]으로 거부한다 — 조용히 첫 페이지로 되돌리면
 *  클라이언트가 무한 루프에 빠질 수 있다. */
fun decodeCursor(cursor: String): CommunityCursor = try {
    val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
    val (instant, id) = decoded.split("|", limit = 2)
    CommunityCursor(createdAt = Instant.parse(instant), id = id.toLong())
} catch (e: RuntimeException) {
    throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
}

/**
 * 거리순 피드의 커서. 정렬 키 (거리, createdAt, id)를 담는다.
 *
 * 오프셋이 아니라 **키셋**이다 — 좌표를 글 쪽에 복사해 둔 덕분에 거리가 실제 값이 되었고,
 * 그래서 페이징 도중 글이 추가돼도 항목이 밀리지 않는다.
 *
 * `d:` 접두어로 최신순 커서와 구분한다. 정렬을 바꾸면서 이전 커서를 그대로 넘기면
 * 조용히 엉뚱한 위치로 가는 대신 오류가 난다.
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
