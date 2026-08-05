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

fun encodeCursor(createdAt: Instant, id: Long): String =
    Base64.getUrlEncoder().withoutPadding()
        .encodeToString("$createdAt|$id".toByteArray(StandardCharsets.UTF_8))

/** 조작·구버전 커서는 [IllegalArgumentException]으로 거부한다 — 조용히 첫 페이지로 되돌리면
 *  클라이언트가 무한 루프에 빠질 수 있다. */
fun decodeCursor(cursor: String): CommunityCursor =
    try {
        val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
        val (instant, id) = decoded.split("|", limit = 2)
        CommunityCursor(createdAt = Instant.parse(instant), id = id.toLong())
    } catch (e: RuntimeException) {
        throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
    }
