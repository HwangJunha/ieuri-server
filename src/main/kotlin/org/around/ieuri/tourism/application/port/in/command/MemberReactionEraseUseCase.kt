package org.around.ieuri.tourism.application.port.`in`.command

/**
 * 탈퇴 회원의 관광 데이터 삭제(SPEC-0011 R17, SPEC-0030 §9.1).
 *
 * **auth가 이 포트를 직접 호출하지 않는다** — `MemberWithdrawn` 이벤트를 구독한 인바운드
 * 어댑터가 부른다. 도메인 간 직접 호출을 만들지 않는 것이 이 구조의 이유다.
 */
interface MemberReactionEraseUseCase {
    /** 그 회원의 좋아요/싫어요 기록을 지운다. 지울 것이 없어도 오류가 아니다. */
    fun eraseMemberReactions(memberId: Long)

    /**
     * 그 회원의 전국 지도 데이터(방문 기록·공유 링크)를 지운다(SPEC-0030 §9.1).
     *
     * **방문 기록은 별도 보관 기간을 두지 않고 탈퇴와 함께 사라진다.** 시각이 없다 해도
     * 시군구 코드의 집합은 그 사람이 어디를 다녔는지이며, 탈퇴 후에 남길 근거가 없다.
     *
     * **공유 링크도 함께 무효가 된다** — 링크를 가진 사람이 탈퇴한 회원의 지도를 계속 열 수
     * 있으면 안 된다. 폐기가 아니라 삭제인 이유는 남길 것이 없기 때문이다.
     */
    fun eraseMemberMapData(memberId: Long)
}
