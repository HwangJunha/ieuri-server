package org.around.ieuri.tourism.application.port.out.persistence

/**
 * 지도 관계의 쓰기 포트(SPEC-0030).
 * - 관계는 방향이 있는 두 행이다. 등록은 양방향을 만들고, 삭제는 자기 행만 지운다.
 */
interface MapRelationPort {

    /**
     * 두 회원을 서로의 목록에 넣는다. 이미 맺어져 있으면 무시된다.
     * - 자기 자신과의 등록은 호출자가 먼저 막는다. 여기까지 오면 DB CHECK가 거절한다.
     */
    fun relate(memberId: Long, counterpartId: Long)

    /** 내 목록에서만 지운다. 없던 관계면 오류가 아니라 false다. */
    fun removeFromMyList(memberId: Long, counterpartId: Long): Boolean

    /** 탈퇴 회원과 얽힌 관계를 양쪽 방향 모두 지우고 지운 건수를 돌려준다. */
    fun deleteAllInvolving(memberId: Long): Int
}
