package org.around.ieuri.auth.adapter.persistence.mapper

import org.around.ieuri.auth.adapter.persistence.table.MemberAccessLogTable
import org.around.ieuri.auth.adapter.persistence.table.MemberAuditLogTable
import org.around.ieuri.auth.adapter.persistence.table.MemberLocationAccessLogTable
import org.around.ieuri.auth.domain.audit.LocationAccessLog
import org.around.ieuri.auth.domain.audit.MemberAccessLog
import org.around.ieuri.auth.domain.audit.MemberAuditLog
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

/** 감사 기록 → 행. payload는 호출자가 JSON으로 직렬화해 넘긴다. id와 시각은 DB가 채운다. */
fun MemberAuditLog.writeTo(row: UpdateBuilder<*>, payloadJson: String) {
    row[MemberAuditLogTable.memberId] = memberId
    row[MemberAuditLogTable.eventType] = eventType
    row[MemberAuditLogTable.payload] = payloadJson
}

/** 접속기록 → 행. */
fun MemberAccessLog.writeTo(row: UpdateBuilder<*>) {
    row[MemberAccessLogTable.memberId] = memberId
    row[MemberAccessLogTable.operation] = operation
    row[MemberAccessLogTable.ipAddress] = ipAddress
}

/** 위치 조회 기록 → 행. payload는 호출자가 JSON으로 직렬화해 넘긴다. */
fun LocationAccessLog.writeTo(row: UpdateBuilder<*>, payloadJson: String) {
    row[MemberLocationAccessLogTable.memberId] = memberId
    row[MemberLocationAccessLogTable.payload] = payloadJson
}
