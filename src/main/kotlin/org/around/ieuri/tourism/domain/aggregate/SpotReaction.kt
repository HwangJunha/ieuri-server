package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.ReactionType

/**
 * 회원의 관광지 좋아요/싫어요 기록. 애그리거트 루트다.
 *
 * **식별 = (memberId, catalogId).** 이전에는 `(memberId, spotId)`였다 — `spot.id`가 언어별이라
 * 일본어로 누른 좋아요가 영어로 바꾸면 사라졌다(SPEC-0002가 "알려진 한계"로 남긴 문제).
 * 카탈로그는 장소 단위라 언어가 식별에서 완전히 빠진다(SPEC-0019 R-10).
 *
 * [spotId]는 **어느 언어 화면에서 눌렀는가**의 기록일 뿐 식별의 일부가 아니다. 이관이
 * 잘못됐을 때 되돌릴 원본으로 남긴다.
 *
 * 재평가는 같은 키에 대한 덮어쓰기(upsert)이며 이력을 남기지 않는다.
 * 취소(clear)는 이번 범위에서 정의하지 않는다.
 */
class SpotReaction private constructor(
    val memberId: Long,
    val catalogId: Long,
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
            catalogId: Long,
            spotId: Long,
            reaction: ReactionType,
        ): SpotReaction {
            require(memberId > 0) { "memberId는 양수여야 합니다." }
            require(catalogId > 0) { "catalogId는 양수여야 합니다." }
            require(spotId > 0) { "spotId는 양수여야 합니다." }
            return SpotReaction(memberId, catalogId, spotId, reaction)
        }
    }
}
