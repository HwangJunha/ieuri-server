package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

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
