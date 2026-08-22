package org.around.ieuri.tourism.application.port.out.persistence

/**
 * 지도 관계의 쓰기 포트(SPEC-0030 확장).
 *
 * **관계는 방향이 있는 두 행이다.** 등록은 (A→B)와 (B→A)를 함께 만들고, 삭제는 자기 행만
 * 지운다 — 내 화면에서 누굴 안 볼지는 내가 정하고, 상대 목록은 건드리지 않는다.
 */
interface MapRelationPort {

    /**
     * 두 회원을 서로의 목록에 넣는다. **이미 맺어져 있으면 조용히 무시된다**(멱등).
     *
     * 자기 자신과의 등록은 호출자가 먼저 막는다 — 여기까지 오면 DB CHECK가 거절한다.
     */
    fun relate(memberId: Long, counterpartId: Long)

    /**
     * **내 목록에서만** 지운다. 상대 목록의 나는 남는다.
     *
     * @return 실제로 지웠으면 true. 없던 관계면 false이며 오류가 아니다.
     */
    fun removeFromMyList(memberId: Long, counterpartId: Long): Boolean

    /**
     * 회원과 얽힌 관계를 전부 지운다(탈퇴, §6).
     *
     * **양쪽 방향을 모두 지운다** — 상대 목록에 남은 탈퇴자는 닉네임을 얻을 수 없어
     * 화면이 깨진다.
     *
     * @return 지운 건수.
     */
    fun deleteAllInvolving(memberId: Long): Int
}
