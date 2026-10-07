package org.around.ieuri.tourism.application.footprint.required

/*
 * 공유 지도가 다른 도메인에 묻는 두 가지(SPEC-0030). 상대 도메인의 read DTO는 이 경계를 넘지 않는다.
 */

/** 지도 주인을 표시하기 위한 최소 정보. 이메일 등 다른 개인정보는 넘어오지 않는다. */
interface MapOwnerLookupPort {

    /** 없는 회원(탈퇴 등)이면 null이다. */
    fun findNickname(memberId: Long): String?

    /** 여러 회원의 닉네임을 한 번에. 없는 회원은 결과에 키가 없다. */
    fun findNicknames(memberIds: Collection<Long>): Map<Long, String>
}
