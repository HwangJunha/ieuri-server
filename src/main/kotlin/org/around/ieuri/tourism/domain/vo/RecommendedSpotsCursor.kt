package org.around.ieuri.tourism.domain.vo

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * 추천 목록 커서(SPEC-0035 R4). `r1|{위치}|{발급 epoch초}`를 base64url로 담는다 — 다른 커서
 * (`SearchCursors.kt`)와 같은 모양이다. 서버 상태가 없어 커서만으로 같은 순열을 재계산한다.
 *
 * [position]은 이 커서 **다음** 항목의 순서상 위치(0-base)다. [issuedAt]은 세션이 시작된 시각이며
 * 이어 받아도 갱신하지 않는다 — 수명은 세션 전체에 걸린다. 조건(좌표·반경·카테고리)은 담지 않는다.
 * 조건을 바꾸면 클라이언트가 커서를 버리고 새로 시작한다.
 */
data class RecommendedSpotsCursor(
    val position: Int,
    val issuedAt: Instant,
) {
    init {
        require(position >= 0) { "커서 위치는 음수일 수 없습니다." }
    }

    fun encode(): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$PREFIX|$position|${issuedAt.epochSecond}".toByteArray(StandardCharsets.UTF_8))

    fun isExpired(now: Instant, maxAge: Duration): Boolean = issuedAt.plus(maxAge).isBefore(now)

    companion object {
        private const val PREFIX = "r1"

        /** 형식이 깨진 커서는 [IllegalArgumentException]이다 — 조용히 첫 페이지로 되돌리지 않는다. */
        fun decode(cursor: String): RecommendedSpotsCursor =
            try {
                val parts = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("|")
                require(parts.size == 3 && parts[0] == PREFIX) { "추천 목록 커서가 아닙니다." }
                RecommendedSpotsCursor(position = parts[1].toInt(), issuedAt = Instant.ofEpochSecond(parts[2].toLong()))
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
            }
    }
}
