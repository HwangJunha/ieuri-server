package org.around.ieuri.common.pagination

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * 커서 문자열의 바깥 포장(base64url, 패딩 없음). 안의 내용(정렬 키)은 각 쿼리 어댑터가 정한다.
 * - 포장 형식이 바뀌면 클라이언트가 들고 있는 커서가 모두 깨진다.
 */
object CursorToken {
    fun wrap(payload: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(StandardCharsets.UTF_8))

    /** 포장을 벗긴다. base64url이 아니면 [IllegalArgumentException]이다. */
    fun unwrap(token: String): String = String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8)
}
