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
