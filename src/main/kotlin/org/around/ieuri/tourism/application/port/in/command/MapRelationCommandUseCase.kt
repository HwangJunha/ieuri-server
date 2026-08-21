package org.around.ieuri.tourism.application.port.`in`.command

/** 지도 관계 쓰기 use case(SPEC-0030 확장). */
interface MapRelationCommandUseCase {

    /**
     * 공유 링크로 관계를 맺는다. **양쪽 목록에 서로 추가된다.**
     *
     * 등록하는 쪽이 자기 기록을 자기 의사로 내놓는 것이라 링크 주인의 허락이 필요 없다.
     * 이미 맺어진 상대면 오류가 아니라 그대로 성공한다(멱등).
     *
     * @return 상대 회원 id.
     */
    fun createRelation(memberId: Long, shareUrl: String): Long

    /**
     * **내 목록에서만** 지운다. 상대 목록의 나는 남는다.
     *
     * @return 실제로 지웠으면 true. 없던 관계면 false이며 오류가 아니다.
     */
    fun removeRelation(memberId: Long, counterpartId: Long): Boolean
}
