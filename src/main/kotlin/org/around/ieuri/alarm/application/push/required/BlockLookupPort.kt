package org.around.ieuri.alarm.application.push.required

/** 차단 관계 조회(SPEC-0028 R9, POL-041). 구현이 community의 `port/in`을 부른다. */
interface BlockLookupPort {
    /** 두 회원이 어느 방향으로든 차단 관계면 true. */
    fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean
}
