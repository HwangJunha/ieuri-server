package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkIssuePort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkRecord
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/**
 * 공유 링크 발급 전용 어댑터(SPEC-0030 R12). `ON CONFLICT DO NOTHING`이라 생 SQL이다.
 * - select-then-insert로 쓰면 동시 발급에서 링크가 둘 생긴다.
 */
@Component
class MapShareLinkIssueJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : MapShareLinkIssuePort {

    /**
     * 회원당 1건(부분 UNIQUE, `revoked_at IS NULL`)을 발급하거나 기존 것을 돌려준다. 충돌하면 [token]은 버려진다.
     * - 충돌 대상을 명시해야 한다 — 없으면 토큰 충돌까지 삼켜 뒤따르는 조회가 빈 결과가 된다.
     */
    @Transactional
    override fun findOrCreate(memberId: Long, token: String): MapShareLinkRecord {
        val inserted = jdbcTemplate.query(
            """
            INSERT INTO tour.map_share_link (member_id, token)
            VALUES (?, ?)
            ON CONFLICT (member_id) WHERE revoked_at IS NULL DO NOTHING
            RETURNING member_id, token, created_at
            """.trimIndent(),
            { rs, _ -> rs.toRecord() },
            memberId,
            token,
        ).firstOrNull()
        if (inserted != null) return inserted

        return jdbcTemplate.query(
            """
            SELECT member_id, token, created_at
            FROM tour.map_share_link
            WHERE member_id = ? AND revoked_at IS NULL
            """.trimIndent(),
            { rs, _ -> rs.toRecord() },
            memberId,
        ).first()
    }

    private fun ResultSet.toRecord(): MapShareLinkRecord = MapShareLinkRecord(
        memberId = getLong("member_id"),
        token = getString("token"),
        createdAt = getTimestamp("created_at").toInstant(),
    )
}
