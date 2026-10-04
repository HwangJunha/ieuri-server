package org.around.ieuri.community.domain.post

/**
 * 사용자가 직접 입력한 글 태그.
 * - [name]은 입력 그대로 보존하고, 매칭은 [normalized](소문자)로 한다.
 */
data class PostTag(
    val name: String,
    val normalized: String,
) {
    companion object {
        const val MAX_LENGTH = 30
        const val MAX_PER_POST = 5

        /** 글에 붙일 태그. 형식이 어긋나면 버리지 않고 [InvalidTagException]으로 거부한다. */
        fun parse(raw: String): PostTag = parseOrNull(raw) ?: throw InvalidTagException()

        /** 입력을 태그로. 앞뒤 공백과 선행 `#`을 제거한다. 비었거나 길거나 공백이 섞이면 null(조회 필터는 무시한다). */
        fun parseOrNull(raw: String): PostTag? {
            val trimmed = raw.trim().removePrefix("#").trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
            if (trimmed.any { it.isWhitespace() }) return null
            return PostTag(name = trimmed, normalized = trimmed.lowercase())
        }
    }
}
