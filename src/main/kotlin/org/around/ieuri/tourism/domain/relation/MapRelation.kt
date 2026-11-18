package org.around.ieuri.tourism.domain.relation

/**
 * 지도 관계 한 방향(SPEC-0030 확장). [ownerId]의 목록에 [counterpartId]가 있다.
 * - 맺을 때는 양방향 두 행이 함께 생기고, 지울 때는 내 목록의 한 행만 사라진다.
 */
data class MapRelation(
    val ownerId: Long,
    val counterpartId: Long,
) {
    init {
        if (ownerId == counterpartId) throw CannotRelateSelfException()
    }

    /** 상대 목록에 들어갈 반대 방향. */
    fun reversed(): MapRelation = MapRelation(ownerId = counterpartId, counterpartId = ownerId)
}
