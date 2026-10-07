package org.around.ieuri.tourism.domain.recommendation

/** 취향 요인이 성립하는 최소 반응 수(SPEC-0035). 좋아요 + 싫어요가 [minReactions] 이상이어야 한다(경계값 포함). */
class TasteThreshold(private val minReactions: Int) {
    init {
        require(minReactions >= 1) { "취향 최소 반응 수는 1 이상이어야 합니다." }
    }

    fun isMetBy(liked: Set<Long>, disliked: Set<Long>): Boolean = liked.size + disliked.size >= minReactions
}
