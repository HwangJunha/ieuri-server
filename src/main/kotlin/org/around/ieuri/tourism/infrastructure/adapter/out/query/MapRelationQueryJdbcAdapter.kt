package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.MapRelationQueryPort
import org.around.ieuri.tourism.application.query.MapRelationOverlap
import org.around.ieuri.tourism.domain.vo.RelationCursor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

/**
 * 지도 관계 조회 어댑터(SPEC-0030 확장).
 *
 * **집계라 생 SQL이다.** 관계 목록 한 페이지를 그리려면 각 상대의 방문 칸 집합이 필요한데,
 * 상대마다 따로 읽으면 20명에 왕복이 40번이 된다. 방문은 회원당 최대 252행이고 칸은
 * 161개라 관계가 수백이어도 전체가 수만 행을 넘지 않아 한 문장으로 끝난다.
 */
@Component
class MapRelationQueryJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : MapRelationQueryPort {

    @Transactional(readOnly = true)
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

        return jdbcTemplate.query(
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
                // JDBC 배열은 박싱된 원소를 담아 온다(PostgreSQL 드라이버는 Long[]). Array<Long>으로
                // 캐스팅하면 코틀린 Long으로 해석돼 런타임에 깨지므로, Number로 받아 내린다.
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
