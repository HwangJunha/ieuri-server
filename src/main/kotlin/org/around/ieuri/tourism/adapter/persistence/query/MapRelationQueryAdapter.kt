package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationOverlap
import org.around.ieuri.tourism.application.relation.required.MapRelationQueryPort
import org.around.ieuri.tourism.domain.shared.RelationCursor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp

/** 지도 관계 조회 어댑터(SPEC-0030 확장). 상대별 겹친 칸까지 한 문장으로 집계하느라 생 SQL이다. */
@Component
class MapRelationQueryAdapter(
    private val exposedJdbcTemplate: JdbcTemplate,
) : MapRelationQueryPort {
    override fun findRelations(
        memberId: Long,
        limit: Int,
        after: RelationCursor?,
    ): List<MapRelationOverlap> {
        val params = mutableListOf<Any>(memberId, memberId)

        val seek = if (after != null) {
            params += Timestamp.from(after.createdAt)
            params += Timestamp.from(after.createdAt)
            params += after.counterpartId
            "AND (r.created_at < ? OR (r.created_at = ? AND r.counterpart_id < ?))"
        } else {
            ""
        }
        params += limit

        return exposedJdbcTemplate.query(
            """
            WITH mine AS (
                SELECT DISTINCT mcd.cell_id
                FROM tour.member_district_visit v
                JOIN tour.map_cell_district mcd ON mcd.district_code = v.district_code
                WHERE v.member_id = ?
            ),
            page AS (
                SELECT r.counterpart_id, r.created_at
                FROM tour.map_relation r
                WHERE r.owner_id = ?
                $seek
                ORDER BY r.created_at DESC, r.counterpart_id DESC
                LIMIT ?
            ),
            theirs AS (
                SELECT p.counterpart_id, p.created_at, mcd.cell_id
                FROM page p
                LEFT JOIN tour.member_district_visit v ON v.member_id = p.counterpart_id
                LEFT JOIN tour.map_cell_district mcd ON mcd.district_code = v.district_code
                GROUP BY p.counterpart_id, p.created_at, mcd.cell_id
            )
            SELECT t.counterpart_id,
                   t.created_at,
                   -- DISTINCT는 위 CTE들의 중복 제거에 기대지 않기 위한 것이다. 지금은 조인이
                   -- 1:1이라 없어도 같은 값이지만, 그렇다면 이 수는 조인 다중도에 의존하게 된다.
                   count(DISTINCT t.cell_id)                               AS their_total,
                   coalesce(
                       array_agg(t.cell_id) FILTER (WHERE m.cell_id IS NOT NULL),
                       '{}'::bigint[]
                   )                                                       AS overlap_cell_ids
            FROM theirs t
            LEFT JOIN mine m ON m.cell_id = t.cell_id
            GROUP BY t.counterpart_id, t.created_at
            ORDER BY t.created_at DESC, t.counterpart_id DESC
            """.trimIndent(),
            { rs, _ ->
                // JDBC 배열 원소는 박싱돼 온다 — Array<Long>으로 캐스팅하면 런타임에 깨지므로 Number로 받아 내린다.
                val ids = (rs.getArray("overlap_cell_ids").array as Array<*>)
                    .map { (it as Number).toLong() }
                MapRelationOverlap(
                    counterpartId = rs.getLong("counterpart_id"),
                    overlappingCellIds = ids.sorted(),
                    theirVisitedCount = rs.getInt("their_total"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            },
            *params.toTypedArray(),
        )
    }
}
