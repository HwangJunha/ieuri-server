package org.around.ieuri.alarm.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object NotificationPreferenceTable : Table("alarm.notification_preference") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id")
    val category = varchar("category", 20)
    val enabled = bool("enabled")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_alarm_notification_preference", memberId, category)
        index("idx_alarm_notification_preference_member", false, memberId)
        check("ck_alarm_notification_preference_category") { category inList listOf("MESSAGE", "SPOT_QA") }
    }
}
