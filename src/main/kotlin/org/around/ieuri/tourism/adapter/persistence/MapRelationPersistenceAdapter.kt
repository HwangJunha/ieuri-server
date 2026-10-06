package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.adapter.persistence.table.MapRelationTable
import org.around.ieuri.tourism.application.relation.required.MapRelationRepository
import org.around.ieuri.tourism.domain.relation.MapRelation
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.springframework.stereotype.Repository

/** 지도 관계 저장소 구현(SPEC-0030 확장). 관계는 양방향 두 행이다. */
@Repository
class MapRelationPersistenceAdapter : MapRelationRepository {

    /** 양방향 두 행을 한 문장으로 만든다. 이미 맺어져 있으면 오류가 아니다(`ON CONFLICT DO NOTHING`, 멱등). */
    override fun relate(relation: MapRelation) {
        MapRelationTable.batchInsert(listOf(relation, relation.reversed()), ignore = true, shouldReturnGeneratedValues = false) {
            this[MapRelationTable.ownerId] = it.ownerId
            this[MapRelationTable.counterpartId] = it.counterpartId
        }
    }

    /** 내 목록에서만 지운다. 상대 행은 남는다. */
    override fun removeFromMyList(memberId: Long, counterpartId: Long): Boolean = MapRelationTable.deleteWhere {
        (MapRelationTable.ownerId eq memberId) and (MapRelationTable.counterpartId eq counterpartId)
    } > 0

    /** 탈퇴 정리 — 양쪽 방향을 모두 지운다(§6). */
    override fun deleteAllInvolving(memberId: Long): Int = MapRelationTable.deleteWhere {
        (MapRelationTable.ownerId eq memberId) or (MapRelationTable.counterpartId eq memberId)
    }
}
