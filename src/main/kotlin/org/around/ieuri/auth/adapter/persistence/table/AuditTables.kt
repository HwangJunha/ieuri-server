package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object MemberAuditLogTable : Table("auth.member_audit_log") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id")
    val eventType = varchar("event_type", 50)

    /** 직렬화한 JSON 문자열을 그대로 받는다. */
    val payload = jsonb<String>("payload", { it }, { it })
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_audit_member", false, memberId)
        index("idx_audit_created_at", false, createdAt)
    }
}

object MemberAccessLogTable : Table("auth.member_access_log") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id")
    val operation = varchar("operation", 200)
    val ipAddress = varchar("ip_address", 45).nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_access_log_created_at", false, createdAt)
        index("idx_access_log_member", false, memberId)
    }
}

object MemberLocationAccessLogTable : Table("auth.member_location_access_log") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id")

    /** 직렬화한 JSON 문자열을 그대로 받는다. */
    val payload = jsonb<String>("payload", { it }, { it })
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_location_access_created_at", false, createdAt)
        index("idx_location_access_member", false, memberId)
    }
}
