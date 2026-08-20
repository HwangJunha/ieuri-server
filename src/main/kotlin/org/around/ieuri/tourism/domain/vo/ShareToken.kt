package org.around.ieuri.tourism.domain.vo

import java.security.SecureRandom
import java.util.Base64

/**
 * 공유 링크 토큰(SPEC-0030 R12).
 *
 * **추측 가능하면 안 된다.** 이 토큰 하나로 인증 없이 남의 방문 지도를 열 수 있으므로, 순차
 * id나 회원 id에서 유도한 값은 쓸 수 없다 — 하나를 알면 나머지를 훑을 수 있다.
 *
 * 192비트(24바이트) CSPRNG를 base64url로 담아 32자가 된다. 만료가 없어(§11) 토큰이 오래
 * 살아 있는 것이 전제이므로 넉넉히 잡았다.
 */
object ShareToken {

    private const val BYTE_LENGTH = 24

    /** `SecureRandom`은 스레드 안전하며 재사용이 권장된다. */
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(BYTE_LENGTH)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
