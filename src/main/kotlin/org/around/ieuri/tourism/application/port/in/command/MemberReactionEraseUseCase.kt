package org.around.ieuri.tourism.application.port.`in`.command

/**
 * 탈퇴 회원의 관광 데이터 삭제(SPEC-0011 R17).
 *
 * **auth가 이 포트를 직접 호출하지 않는다** — `MemberWithdrawn` 이벤트를 구독한 인바운드
 * 어댑터가 부른다. 도메인 간 직접 호출을 만들지 않는 것이 이 구조의 이유다.
 */
interface MemberReactionEraseUseCase {
    /** 그 회원의 좋아요/싫어요 기록을 지운다. 지울 것이 없어도 오류가 아니다. */
    fun eraseMemberReactions(memberId: Long)
}
