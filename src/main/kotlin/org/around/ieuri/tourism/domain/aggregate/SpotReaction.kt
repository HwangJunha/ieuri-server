package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType

/**
 * 회원의 관광지 좋아요/싫어요 기록. 식별은 (memberId, catalogId, language)다(SPEC-0029 R-13).
 * - 재평가는 같은 키에 덮어쓰며 이력을 남기지 않는다. 취소는 없다.
 */
class SpotReaction private constructor(
    val memberId: Long,
    val catalogId: Long,
    val language: DomainLocale,
    reaction: ReactionType,
) {
    var reaction: ReactionType = reaction
        private set

    /** 재평가: 같은 키에 다른 판단을 덮어쓴다. */
    fun reevaluate(newReaction: ReactionType) {
        this.reaction = newReaction
    }

    companion object {
        /** 새 기록 생성. */
        fun record(
            memberId: Long,
            catalogId: Long,
            language: DomainLocale,
            reaction: ReactionType,
        ): SpotReaction {
            require(memberId > 0) { "memberId는 양수여야 합니다." }
            require(catalogId > 0) { "catalogId는 양수여야 합니다." }
            return SpotReaction(memberId, catalogId, language, reaction)
        }
    }
}
