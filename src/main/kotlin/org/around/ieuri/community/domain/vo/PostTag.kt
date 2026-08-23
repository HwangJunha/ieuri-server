package org.around.ieuri.community.domain.vo

/**
 * 글 태그. **사용자가 직접 입력한다** — 고정 세트가 아니다.
 *
 * [name]은 유저가 친 그대로 보존하고 [normalized]로 매칭한다. "Seoul"과 "seoul"이 같은
 * 태그여야 하지만, 화면에는 유저가 쓴 대로 보여야 하기 때문이다.
 */
data class PostTag(
    val name: String,
    val normalized: String,
) {
    companion object {
        const val MAX_LENGTH = 30
        const val MAX_PER_POST = 5

        /**
         * 입력을 태그로. 앞뒤 공백과 선행 `#`을 제거한다 — 유저가 "#서울"로 쳐도 "서울"과
         * 같은 태그여야 한다. 형식이 어긋나면 null이며, 호출자가 거부 여부를 정한다.
         */
        fun parseOrNull(raw: String): PostTag? {
            val trimmed = raw.trim().removePrefix("#").trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
            // 공백이 섞인 태그는 검색이 애매해진다("맛집 추천"이 한 태그인지 둘인지).
            if (trimmed.any { it.isWhitespace() }) return null
            return PostTag(name = trimmed, normalized = trimmed.lowercase())
        }

        /** 저장소에서 복원. 정규화는 적재 시점에 끝났으므로 다시 하지 않는다. */
        fun restore(name: String, normalized: String) = PostTag(name, normalized)
    }
}
