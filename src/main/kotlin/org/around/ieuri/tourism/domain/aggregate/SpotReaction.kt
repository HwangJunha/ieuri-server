package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType

/**
 * 회원의 관광지 좋아요/싫어요 기록. 애그리거트 루트이자 DB에 쓰는 유일한 도메인(관광 데이터는 적재 안 함).
 *
 * 식별 = (memberId, contentId, language). 같은 장소라도 언어별 contentId가 다르므로 판단이 기록된 언어를
 * 함께 담는다(AC-12). 재평가는 같은 키에 대한 덮어쓰기(upsert)이며 이력을 남기지 않는다(AC-13).
 * 취소(clear)는 이번 범위에서 정의하지 않는다.
 */
class SpotReaction private constructor(
    val memberId: Long,
    val contentId: String,
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
        /** 새 기록 생성(또는 재평가 대상 도메인 상태). */
        fun record(
            memberId: Long,
            contentId: String,
            language: DomainLocale,
            reaction: ReactionType,
        ): SpotReaction {
            require(memberId > 0) { "memberId는 양수여야 합니다." }
            require(contentId.isNotBlank()) { "contentId는 필수입니다." }
            return SpotReaction(memberId, contentId, language, reaction)
        }
    }
}
