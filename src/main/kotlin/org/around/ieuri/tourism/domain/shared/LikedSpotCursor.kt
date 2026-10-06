package org.around.ieuri.tourism.domain.shared

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

/**
 * 좋아요 목록의 keyset 커서(SPEC-0019 R-10). 정렬 키는 `(updated_at, catalog_id)`다.
 * - 버전 접두사로 `spot.id` 시절의 구 커서를 거부한다 — 형식이 같아 파싱은 성공하기 때문이다.
 * - 시각은 ISO-8601로 담아 나노초까지 보존한다.
 */
data class LikedSpotCursor(val updatedAt: Instant, val catalogId: Long) {

    fun encode(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("$PREFIX|$updatedAt|$catalogId".toByteArray(StandardCharsets.UTF_8))

    companion object {
        /** 참조 축이 카탈로그로 바뀐 뒤의 형식. 구 커서에는 이 필드가 없다. */
        private const val PREFIX = "v2"

        /** 커서를 정렬 키로 되돌린다. 접두사가 없는 구 커서는 값 해석 전에 거부한다. */
        fun decode(cursor: String): LikedSpotCursor = try {
            val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            val parts = decoded.split("|")
            // 필드 수와 접두사를 먼저 본다.
            require(parts.size == 3 && parts[0] == PREFIX) { "구버전 커서입니다." }
            LikedSpotCursor(updatedAt = Instant.parse(parts[1]), catalogId = parts[2].toLong())
        } catch (e: RuntimeException) {
            // 첫 페이지로 되돌리지 않고 거부한다. 애플리케이션 계층이 INVALID_CURSOR로 승격한다.
            throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
        }
    }
}
