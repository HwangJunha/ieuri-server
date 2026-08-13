package org.around.ieuri.tourism.domain.vo

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

/**
 * 좋아요 목록의 keyset 커서(SPEC-0019 R-10). 정렬 키는 `(updated_at, catalog_id)`다.
 *
 * **버전 접두사가 있어야 하는 이유는 형식이 같기 때문이다.** 참조 축이 `spot.id`에서
 * `catalog_id`로 바뀌었는데 둘 다 `Long`이라, 접두사가 없으면 이관 이전에 발급된 커서가
 * **파싱에 성공한 뒤 잘못된 keyset 조건으로 조용히 다른 페이지를 반환한다.** 예외가 나지
 * 않아 발견이 늦는 종류의 실패다.
 *
 * 시각은 ISO-8601로 담아 나노초까지 보존한다. 잘라내면 같은 마이크로초의 두 행에서 커서가
 * 어긋나 항목을 건너뛴다.
 */
data class LikedSpotCursor(val updatedAt: Instant, val catalogId: Long) {

    fun encode(): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$PREFIX|$updatedAt|$catalogId".toByteArray(StandardCharsets.UTF_8))

    companion object {
        /** 참조 축이 카탈로그로 바뀐 뒤의 형식임을 나타낸다. 구 커서에는 이 필드가 아예 없다. */
        private const val PREFIX = "v2"

        /**
         * 커서를 정렬 키로 되돌린다. 접두사가 없는 구 커서는 **값 해석을 시도하기 전에**
         * 거부한다 — 구조가 새 형식과 같아 파싱은 성공하기 때문이다.
         *
         * 이관 시점에 살아 있던 커서는 무효가 되며 사용자는 목록을 처음부터 받는다.
         */
        fun decode(cursor: String): LikedSpotCursor =
            try {
                val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                val parts = decoded.split("|")
                // 필드 수와 접두사를 먼저 본다. 값 해석이 앞서면 구 커서가 통과한다.
                require(parts.size == 3 && parts[0] == PREFIX) { "구버전 커서입니다." }
                LikedSpotCursor(updatedAt = Instant.parse(parts[1]), catalogId = parts[2].toLong())
            } catch (e: RuntimeException) {
                // 조용히 첫 페이지로 되돌리면 클라이언트가 무한 루프에 빠질 수 있어 명시적으로 거부한다.
                // 애플리케이션 계층이 이것을 INVALID_CURSOR로 승격한다 — 도메인은 GraphQL을 알지 않는다.
                throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
            }
    }
}
