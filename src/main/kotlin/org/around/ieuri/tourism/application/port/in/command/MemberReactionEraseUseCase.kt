package org.around.ieuri.tourism.application.port.`in`.command

/** 탈퇴 회원의 관광 데이터 삭제(SPEC-0011, SPEC-0030). `MemberWithdrawn` 이벤트 리스너가 부른다. */
interface MemberReactionEraseUseCase {
    /** 그 회원의 좋아요/싫어요 기록을 지운다. 지울 것이 없어도 오류가 아니다. */
    fun eraseMemberReactions(memberId: Long)

    /** 그 회원의 전국 지도 데이터(방문 기록·공유 링크·양방향 관계)를 삭제한다. */
    fun eraseMemberMapData(memberId: Long)
}
