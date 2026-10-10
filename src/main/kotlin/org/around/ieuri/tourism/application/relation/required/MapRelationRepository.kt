package org.around.ieuri.tourism.application.relation.required

import org.around.ieuri.tourism.domain.relation.MapRelation

/**
 * 지도 관계 저장소(SPEC-0030).
 * - 관계는 방향이 있는 두 행이다. 등록은 양방향을 만들고, 삭제는 자기 행만 지운다.
 */
interface MapRelationRepository {

    /** [relation]과 그 반대 방향을 함께 넣는다. 이미 맺어져 있으면 무시된다(멱등). */
    fun relate(relation: MapRelation)

    /** 내 목록에서만 지운다. 없던 관계면 오류가 아니라 false다. */
    fun removeFromMyList(memberId: Long, counterpartId: Long): Boolean

    /** 탈퇴 회원과 얽힌 관계를 양쪽 방향 모두 지우고 지운 건수를 돌려준다. */
    fun deleteAllInvolving(memberId: Long): Int
}
