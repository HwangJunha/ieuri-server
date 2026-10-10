package org.around.ieuri.alarm.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object NotificationMessageTable : Table("alarm.notification_message") {
    val id = long("id").autoIncrement()
    val notificationType = varchar("notification_type", 30)
    val locale = varchar("locale", 10)
    val title = varchar("title", 200)
    val body = varchar("body", 200)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_alarm_notification_message", notificationType, locale)
        check("ck_alarm_notification_message_locale") { locale inList listOf("EN", "KO", "JA", "ZH") }
        check("ck_alarm_notification_message_type") { notificationType inList listOf("DM", "SPOT_QA_ANSWER", "SPOT_QA_REPLY") }
    }
}
