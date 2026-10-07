package org.around.ieuri.tourism.domain.shared

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * 커서 커넥션의 keyset 커서 문자열. (정렬 키, id) 쌍을 접두사와 함께 base64url로 감싼다.
 * - 접두사로 종류를 구분하며, 다른 종류의 커서는 거부된다.
 */
object CursorCodec {

    fun encode(prefix: String, sortKey: String, id: Long): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("$prefix:$sortKey|$id".toByteArray(StandardCharsets.UTF_8))

    /**
     * 커서를 (정렬 키 문자열, id)로 되돌린다. 조작·구버전·다른 종류의 커서는 모두
     * [IllegalArgumentException]이다 — 조용히 첫 페이지로 되돌리면 클라이언트가 무한 루프에 빠진다.
     */
    fun decode(prefix: String, cursor: String, label: String): Pair<String, Long> = try {
        val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
        require(decoded.startsWith("$prefix:")) { "$label 커서가 아닙니다." }
        val (key, id) = decoded.removePrefix("$prefix:").split("|", limit = 2)
        key to id.toLong()
    } catch (e: RuntimeException) {
        throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
    }
}
