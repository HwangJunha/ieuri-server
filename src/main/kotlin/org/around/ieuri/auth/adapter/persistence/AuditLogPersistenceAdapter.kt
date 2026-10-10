package org.around.ieuri.auth.adapter.persistence

import org.around.ieuri.auth.adapter.persistence.mapper.writeTo
import org.around.ieuri.auth.adapter.persistence.table.MemberAccessLogTable
import org.around.ieuri.auth.adapter.persistence.table.MemberAuditLogTable
import org.around.ieuri.auth.adapter.persistence.table.MemberLocationAccessLogTable
import org.around.ieuri.auth.application.audit.required.AuditLogRepository
import org.around.ieuri.auth.domain.audit.LocationAccessLog
import org.around.ieuri.auth.domain.audit.MemberAccessLog
import org.around.ieuri.auth.domain.audit.MemberAuditLog
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** 감사·접속·위치 조회 기록 저장소 구현. payload는 JSON으로 직렬화해 jsonb에 넣는다. */
@Repository
class AuditLogPersistenceAdapter(
    private val objectMapper: ObjectMapper,
) : AuditLogRepository {

    override fun insert(log: MemberAuditLog) {
        MemberAuditLogTable.insert { log.writeTo(it, objectMapper.writeValueAsString(log.payload)) }
    }

    override fun insert(log: MemberAccessLog) {
        MemberAccessLogTable.insert { log.writeTo(it) }
    }

    override fun insert(log: LocationAccessLog) {
        MemberLocationAccessLogTable.insert { log.writeTo(it, objectMapper.writeValueAsString(log.payload)) }
    }

    override fun deleteMemberAuditBefore(cutoff: Instant, limit: Int): Int = deleteBefore(MemberAuditLogTable, MemberAuditLogTable.id, MemberAuditLogTable.createdAt, cutoff, limit)

    override fun deleteAccessBefore(cutoff: Instant, limit: Int): Int = deleteBefore(MemberAccessLogTable, MemberAccessLogTable.id, MemberAccessLogTable.createdAt, cutoff, limit)

    override fun deleteLocationAccessBefore(cutoff: Instant, limit: Int): Int = deleteBefore(MemberLocationAccessLogTable, MemberLocationAccessLogTable.id, MemberLocationAccessLogTable.createdAt, cutoff, limit)

    /** 한 회차만 지운다 — 대상 id를 [limit]건 읽고 그 id만 지운다. */
    private fun deleteBefore(table: Table, id: Column<Long>, createdAt: Column<OffsetDateTime>, cutoff: Instant, limit: Int): Int {
        val ids = table.select(id).where { createdAt less cutoff.atOffset(ZoneOffset.UTC) }.limit(limit).map { it[id] }
        if (ids.isEmpty()) return 0
        return table.deleteWhere { id inList ids }
    }
}
