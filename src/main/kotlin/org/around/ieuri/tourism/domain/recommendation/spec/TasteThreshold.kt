package org.around.ieuri.tourism.domain.recommendation.spec

import org.around.ieuri.tourism.domain.recommendation.ReactionHistory
import org.around.ieuri.tourism.domain.shared.Specification

/** 취향 요인이 성립하는 최소 반응 수(SPEC-0035). 좋아요 + 싫어요가 [minReactions] 이상이어야 한다(경계값 포함). */
class TasteThreshold(private val minReactions: Int) : Specification<ReactionHistory> {
    init {
        require(minReactions >= 1) { "취향 최소 반응 수는 1 이상이어야 합니다." }
    }

    override fun isSatisfiedBy(candidate: ReactionHistory): Boolean = candidate.count >= minReactions
}
