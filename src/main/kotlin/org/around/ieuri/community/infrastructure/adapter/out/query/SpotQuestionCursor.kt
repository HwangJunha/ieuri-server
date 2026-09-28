package org.around.ieuri.community.infrastructure.adapter.out.query

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

/**
 * 질문 목록의 keyset 커서. 정렬 키 **(resolved, createdAt, id)를 전부** 담는다.
 *
 * createdAt만 담으면 안 된다 — 정렬이 `resolved ASC, createdAt DESC`라 미해결 구간과 해결
 * 구간이 각각 시간 내림차순이고, 두 구간의 시각이 겹친다. 시각만으로는 어느 구간에서
 * 이어가는지 알 수 없어 경계에서 항목이 중복되거나 누락된다.
 *
 * `q:` 접두어로 [CommunityCursor]와 구분한다. 다른 커넥션의 커서를 그대로 넘기면 조용히
 * 엉뚱한 위치로 가는 대신 오류가 난다.
 *
 * 시각은 ISO-8601로 담아 나노초까지 보존한다 — 잘라내면 같은 마이크로초의 두 행에서
 * 커서가 어긋나 항목을 건너뛴다.
 */
data class SpotQuestionCursor(
    val resolved: Boolean,
    val createdAt: Instant,
    val id: Long,
)

fun encodeSpotQuestionCursor(resolved: Boolean, createdAt: Instant, id: Long): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString("q:$resolved|$createdAt|$id".toByteArray(StandardCharsets.UTF_8))

/**
 * 조작·구버전 커서는 [IllegalArgumentException]으로 거부한다 — 조용히 첫 페이지로 되돌리면
 * 클라이언트가 무한 루프에 빠질 수 있다.
 */
fun decodeSpotQuestionCursor(cursor: String): SpotQuestionCursor = try {
    val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
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
