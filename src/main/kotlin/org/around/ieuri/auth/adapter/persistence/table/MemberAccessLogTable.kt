package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

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
