package org.around.ieuri.tourism.application.relation.provided

/** 지도 관계 쓰기 유스케이스(SPEC-0030 확장). */
interface MapRelationCommandUseCase {

    /**
     * 공유 링크로 관계를 맺는다. 양쪽 목록에 서로 추가되며, 이미 맺어진 상대면 그대로 성공한다(멱등).
     * @return 상대 회원 id.
     */
    fun createRelation(memberId: Long, shareUrl: String): Long

    /**
     * 내 목록에서만 지운다. 상대 목록의 나는 남는다.
     * @return 실제로 지웠으면 true. 없던 관계면 오류가 아니라 false다.
     */
    fun removeRelation(memberId: Long, counterpartId: Long): Boolean

    /** 탈퇴 정리. 양쪽 방향을 모두 지운다. 탈퇴 커밋 뒤 리스너가 부르며 새 트랜잭션이다. 지운 건수(로그용). */
    fun eraseMemberContent(memberId: Long): Int
}
