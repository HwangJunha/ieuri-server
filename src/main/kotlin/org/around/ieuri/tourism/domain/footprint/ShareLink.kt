package org.around.ieuri.tourism.domain.footprint

import java.time.Instant

/**
 * 발자취 공유 링크(SPEC-0030 R12). 토큰 하나로 인증 없이 지도를 연다.
 * - [issuedAt]이 null이면 아직 저장되지 않은 새 링크다.
 */
data class ShareLink(
    val token: String,
    val issuedAt: Instant?,
) {
    init {
        require(token.isNotBlank()) { "토큰은 비어 있을 수 없습니다." }
    }

    companion object {
        fun issue(): ShareLink = ShareLink(token = ShareToken.generate(), issuedAt = null)
    }
}
