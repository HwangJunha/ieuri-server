package org.around.ieuri.alarm.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object DeviceTokenTable : Table("alarm.device_token") {
    val id = long("id").autoIncrement()
    val deviceId = varchar("device_id", 255)
    val memberId = long("member_id")
    val fcmToken = text("fcm_token")
    val language = varchar("language", 10)
    val platform = varchar("platform", 10)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_alarm_device_token_device", deviceId)
        index("idx_alarm_device_token_member", false, memberId)
        check("ck_alarm_device_token_language") { language inList listOf("EN", "KO", "JA", "ZH") }
        check("ck_alarm_device_token_platform") { platform inList listOf("IOS", "ANDROID") }
    }
}
