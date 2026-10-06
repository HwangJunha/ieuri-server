package org.around.ieuri.tourism.application.reaction.required

import org.around.ieuri.tourism.domain.reaction.SpotReaction

/** 좋아요/싫어요 저장소. 키는 `(memberId, catalogId, language)`다(SPEC-0029 R-13). */
interface SpotReactionRepository {
    /** 새 기록이면 넣고, 있으면 반응을 덮어쓴다. 첫 반응이 동시에 와도 실패하지 않는다. */
    fun save(reaction: SpotReaction)

    /** 탈퇴 회원의 기록 전량 삭제. 지운 건수를 돌려준다. */
    fun deleteByMember(memberId: Long): Int
}
