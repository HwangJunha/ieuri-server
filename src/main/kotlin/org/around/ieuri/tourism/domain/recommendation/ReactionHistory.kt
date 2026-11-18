package org.around.ieuri.tourism.domain.recommendation

/** 회원이 지금 언어에서 반응한 카탈로그 id. 취향 요인을 낼 수 있는지 판정하는 재료다. */
data class ReactionHistory(
    val liked: Set<Long>,
    val disliked: Set<Long>,
) {
    val count: Int get() = liked.size + disliked.size
}
