package org.around.ieuri.tourism.domain.footprint

import java.security.SecureRandom
import java.util.Base64

/**
 * 공유 링크 토큰(SPEC-0030). 192비트 CSPRNG 값을 base64url로 담은 32자다.
 * - 토큰 하나로 인증 없이 지도를 열 수 있으므로 추측 가능한 값(순차 id 등)에서 유도하지 않는다.
 */
object ShareToken {

    private const val BYTE_LENGTH = 24

    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(BYTE_LENGTH)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** 공유 URL의 마지막 경로 조각을 토큰으로 꺼낸다. 스킴과 유효성은 검사하지 않는다. */
    fun extractFrom(shareUrl: String): String = shareUrl.substringAfterLast('/').substringBefore('?').trim()
}
