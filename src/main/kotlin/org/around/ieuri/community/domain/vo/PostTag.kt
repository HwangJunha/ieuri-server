package org.around.ieuri.community.domain.vo

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

        /**
         * 입력을 태그로. 앞뒤 공백과 선행 `#`을 제거한다.
         * - 비었거나 길거나 공백이 섞이면 null이며, 거부 여부는 호출자가 정한다.
         */
        fun parseOrNull(raw: String): PostTag? {
            val trimmed = raw.trim().removePrefix("#").trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
            if (trimmed.any { it.isWhitespace() }) return null
            return PostTag(name = trimmed, normalized = trimmed.lowercase())
        }

        /** 저장소에서 복원. 정규화는 적재 시점에 끝났으므로 다시 하지 않는다. */
        fun restore(name: String, normalized: String) = PostTag(name, normalized)
    }
}
