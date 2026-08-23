package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkIssuePort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkRecord
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/**
 * 공유 링크 발급 전용 어댑터(SPEC-0030 R12).
 *
 * **`ON CONFLICT DO NOTHING`이라 생 SQL이다** — 발급이 "있으면 그것, 없으면 새로"라
 * select-then-insert로 쓰면 동시 발급에서 링크가 둘 생긴다. 이 클래스에 생 SQL이 격리되며,
 * 조회·폐기는 JPA 파생 쿼리를 쓴다.
 */
@Component
class MapShareLinkIssueJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : MapShareLinkIssuePort {

    /**
     * **부분 UNIQUE 인덱스(`revoked_at IS NULL`)가 회원당 1건을 강제한다.** 충돌하면 INSERT가
     * 아무 행도 돌려주지 않으므로 이미 있는 행을 다시 읽는다 — 그 경우 [token]은 버려진다.
     *
     * 충돌 대상을 명시하는 이유는 대상 없이 두면 **토큰 충돌까지 삼켜서** 뒤따르는 조회가 빈
     * 결과를 받고 알 수 없는 오류가 되기 때문이다.
     *
     * 두 문장이지만 같은 트랜잭션 안이라 사이에 폐기가 끼어도 결과가 갈라지지 않는다.
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
