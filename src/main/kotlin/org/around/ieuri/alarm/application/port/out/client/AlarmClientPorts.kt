package org.around.ieuri.alarm.application.port.out.client

/**
 * 차단 관계 조회(SPEC-0028 R9). **양방향이다**(POL-041) — 내가 차단했든 상대가 차단했든
 * 발송하지 않는다.
 *
 * community에 사는 사실이지만 alarm이 그 도메인을 알지 않도록 포트로 뒤집는다. 구현이
 * community의 `port/in`을 부른다.
 */
interface BlockLookupPort {
    /** 두 회원이 어느 방향으로든 차단 관계면 true. */
    fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean
}

/**
 * 알림 제목에 넣을 회원 표시 이름 조회(R13 — 제목은 서버가 조립한다).
 *
 * 본문이 아니라 **제목**에만 쓴다. 사용자가 쓴 본문은 이 포트와 무관하게 원문 그대로 나간다.
 */
interface MemberNicknameLookupPort {
    /** 회원의 닉네임. 회원이 없으면 null이다. */
    fun findNickname(memberId: Long): String?
}
