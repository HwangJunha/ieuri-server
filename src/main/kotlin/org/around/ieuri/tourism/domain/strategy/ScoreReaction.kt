package org.around.ieuri.tourism.domain.strategy

/** 점수 계산에 넘기는 반응 상태(SPEC-0036 D-13). 「반응 없음」을 포함한 세 갈래다. */
enum class ScoreReaction {
    NONE,
    LIKED,
    DISLIKED,
}
