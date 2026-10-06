package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.adapter.persistence.mapper.toMapShareLinkRecord
import org.around.ieuri.tourism.adapter.persistence.table.MapShareLinkTable
import org.around.ieuri.tourism.application.sharelink.required.MapShareLinkRecord
import org.around.ieuri.tourism.application.sharelink.required.MapShareLinkRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/** 공유 링크 저장소 구현(SPEC-0030 R12~R14 · §9.1). */
@Repository
class MapShareLinkPersistenceAdapter(
    private val exposedJdbcTemplate: JdbcTemplate,
) : MapShareLinkRepository {

    /** 유효한 링크만. 폐기됐거나 없으면 null이다(둘을 구분하지 않는다). */
    override fun findActiveByToken(token: String): MapShareLinkRecord? = MapShareLinkTable.selectAll()
        .where { (MapShareLinkTable.token eq token) and MapShareLinkTable.revokedAt.isNull() }
        .singleOrNull()?.toMapShareLinkRecord()

    /**
     * 회원당 1건(부분 UNIQUE, `revoked_at IS NULL`)을 발급하거나 기존 것을 돌려준다. 충돌하면 [token]은 버려진다.
     * - 충돌 대상을 명시해야 한다 — 없으면 토큰 충돌까지 삼켜 뒤따르는 조회가 빈 결과가 된다. 그래서 raw SQL이다.
     * - select-then-insert로 쓰면 동시 발급에서 링크가 둘 생긴다.
     */
    override fun findOrCreate(memberId: Long, token: String): MapShareLinkRecord {
        exposedJdbcTemplate.update(
            """
            INSERT INTO tour.map_share_link (member_id, token)
            VALUES (?, ?)
            ON CONFLICT (member_id) WHERE revoked_at IS NULL DO NOTHING
            """.trimIndent(),
            memberId,
            token,
        )
        return MapShareLinkTable.selectAll()
            .where { (MapShareLinkTable.memberId eq memberId) and MapShareLinkTable.revokedAt.isNull() }
            .single().toMapShareLinkRecord()
    }

    /** 본인 것이 아니거나 이미 폐기됐으면 아무 일도 없다(false). */
    override fun revoke(memberId: Long, token: String): Boolean = MapShareLinkTable.update({
        (MapShareLinkTable.memberId eq memberId) and (MapShareLinkTable.token eq token) and MapShareLinkTable.revokedAt.isNull()
    }) { it[MapShareLinkTable.revokedAt] = CurrentTimestampWithTimeZone } > 0

    /** 탈퇴 시 호출된다 — 폐기가 아니라 삭제다(§9.1). */
    override fun deleteAll(memberId: Long): Int = MapShareLinkTable.deleteWhere { MapShareLinkTable.memberId eq memberId }
}
