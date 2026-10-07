package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.adapter.persistence.table.MapCellDistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.MapRelationTable
import org.around.ieuri.tourism.adapter.persistence.table.MemberDistrictVisitTable
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationOverlap
import org.around.ieuri.tourism.application.relation.required.MapRelationQueryPort
import org.around.ieuri.tourism.domain.relation.RelationCursor
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import java.time.ZoneOffset

/**
 * 지도 관계 조회 어댑터(SPEC-0030 확장). 한 페이지의 상대를 먼저 자르고, 나와 그 상대들의 방문 칸을 한 번에 읽어 겹침을 센다.
 * - 칸은 시군구가 아니라 칸 단위로 센다 — 한 칸의 시군구 하나만 방문해도 그 칸은 방문이다.
 */
@Component
class MapRelationQueryAdapter : MapRelationQueryPort {

    override fun findRelations(memberId: Long, limit: Int, after: RelationCursor?): List<MapRelationOverlap> {
        val page = MapRelationTable.select(MapRelationTable.counterpartId, MapRelationTable.createdAt)
            .where {
                val mine = MapRelationTable.ownerId eq memberId
                if (after == null) {
                    mine
                } else {
                    val at = after.createdAt.atOffset(ZoneOffset.UTC)
                    mine and ((MapRelationTable.createdAt less at) or ((MapRelationTable.createdAt eq at) and (MapRelationTable.counterpartId less after.counterpartId)))
                }
            }
            .orderBy(MapRelationTable.createdAt to SortOrder.DESC, MapRelationTable.counterpartId to SortOrder.DESC)
            .limit(limit)
            .map { it[MapRelationTable.counterpartId] to it[MapRelationTable.createdAt].toInstant() }
        if (page.isEmpty()) return emptyList()

        val cellsByMember = visitedCells(page.map { it.first } + memberId)
        val mine = cellsByMember[memberId].orEmpty()
        return page.map { (counterpartId, createdAt) ->
            val theirs = cellsByMember[counterpartId].orEmpty()
            MapRelationOverlap(
                counterpartId = counterpartId,
                overlappingCellIds = theirs.filter { it in mine }.sorted(),
                theirVisitedCount = theirs.size,
                createdAt = createdAt,
            )
        }
    }

    /** 회원별 방문 칸(중복 없음). 방문이 없는 회원은 키가 없다. */
    private fun visitedCells(memberIds: List<Long>): Map<Long, Set<Long>> = MemberDistrictVisitTable
        .join(MapCellDistrictTable, JoinType.INNER, MapCellDistrictTable.districtCode, MemberDistrictVisitTable.districtCode)
        .select(MemberDistrictVisitTable.memberId, MapCellDistrictTable.cellId)
        .where { MemberDistrictVisitTable.memberId inList memberIds }
        .withDistinct()
        .groupBy({ it[MemberDistrictVisitTable.memberId] }) { it[MapCellDistrictTable.cellId] }
        .mapValues { (_, cells) -> cells.toSet() }
}
