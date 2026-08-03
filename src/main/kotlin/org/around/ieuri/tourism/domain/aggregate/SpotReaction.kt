package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.ReactionType

/**
 * 회원의 관광지 좋아요/싫어요 기록. 애그리거트 루트이자 DB에 쓰는 유일한 도메인(관광 데이터는 적재 안 함).
 *
 * **식별 = (memberId, spotId).** 이전에는 `(memberId, contentId, language)`였다 — TourAPI contentId가
 * 언어별로 갈려 언어를 함께 들어야 했기 때문이다(AC-12). 이제 `spot.id`가 그 조합에 1:1로 대응하므로
 * 언어는 식별의 일부가 아니다. 같은 장소의 다른 언어는 애초에 다른 `spotId`다.
 *
 * 재평가는 같은 키에 대한 덮어쓰기(upsert)이며 이력을 남기지 않는다(AC-13).
 * 취소(clear)는 이번 범위에서 정의하지 않는다.
 */
class SpotReaction private constructor(
    val memberId: Long,
    val spotId: Long,
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
            spotId: Long,
            reaction: ReactionType,
        ): SpotReaction {
            require(memberId > 0) { "memberId는 양수여야 합니다." }
            require(spotId > 0) { "spotId는 양수여야 합니다." }
            return SpotReaction(memberId, spotId, reaction)
        }
    }
}
