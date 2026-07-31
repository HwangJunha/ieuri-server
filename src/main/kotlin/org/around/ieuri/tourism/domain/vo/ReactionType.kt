package org.around.ieuri.tourism.domain.vo

/**
 * 스와이프 판단. 오른쪽=LIKE, 왼쪽=DISLIKE. 취소(clear)는 이번 범위 밖(되돌리기 제외).
 */
enum class ReactionType {
    LIKE,
    DISLIKE,
}
