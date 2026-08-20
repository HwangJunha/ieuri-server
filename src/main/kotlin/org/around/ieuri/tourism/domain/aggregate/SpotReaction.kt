package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType

/**
 * 회원의 관광지 좋아요/싫어요 기록. 애그리거트 루트다.
 *
 * **식별 = (memberId, catalogId, language)**(SPEC-0029 R-13). SPEC-0019가 언어를 뺐던 것을
 * 이 스펙이 되돌린다 — spot 노출 전체가 활성 언어 단위로 분리되므로 좋아요도 같은 축을
 * 따른다. 언어를 바꾸면 그 언어에서 누른 것만 보이며, 이전 언어의 기록은 사라진 것이
 * 아니라 그 언어로 돌아가면 그대로 있다.
 *
 * `spot.id`가 아니라 카탈로그 id인 것은 그대로다 — 언어는 별도 축이고, 같은 언어 안에서
 * 장소는 여전히 카탈로그 단위다.
 *
 * 재평가는 같은 키에 대한 덮어쓰기(upsert)이며 이력을 남기지 않는다.
 * 취소(clear)는 이번 범위에서 정의하지 않는다.
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
        /** 새 기록 생성(또는 재평가 대상 도메인 상태). */
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
