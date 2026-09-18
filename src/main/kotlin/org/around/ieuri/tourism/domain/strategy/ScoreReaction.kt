package org.around.ieuri.tourism.domain.strategy

/**
 * 점수 계산에 넘기는 반응 상태(SPEC-0036 D-13). 도메인 `ReactionType`을 그대로 쓰지 않는 이유는
 * **「반응 없음」이 필요해서**다 — 반응은 있거나 없거나이지만 점수는 세 갈래다.
 */
enum class ScoreReaction {
    NONE,
    LIKED,
    DISLIKED,
}
