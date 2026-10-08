package org.around.ieuri.alarm.adapter.persistence.mapper

import org.around.ieuri.alarm.adapter.persistence.table.DeviceTokenTable
import org.around.ieuri.alarm.adapter.persistence.table.NotificationMessageTable
import org.around.ieuri.alarm.adapter.persistence.table.NotificationPreferenceTable
import org.around.ieuri.alarm.domain.device.AlarmLocale
import org.around.ieuri.alarm.domain.device.DevicePlatform
import org.around.ieuri.alarm.domain.device.DeviceToken
import org.around.ieuri.alarm.domain.preference.NotificationCategory
import org.around.ieuri.alarm.domain.preference.NotificationPreference
import org.around.ieuri.alarm.domain.push.NotificationMessage
import org.around.ieuri.alarm.domain.push.NotificationType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

/** 기기 토큰 행 → 도메인. */
fun ResultRow.toDeviceToken(): DeviceToken = DeviceToken(
    id = this[DeviceTokenTable.id],
    deviceId = this[DeviceTokenTable.deviceId],
    memberId = this[DeviceTokenTable.memberId],
    fcmToken = this[DeviceTokenTable.fcmToken],
    language = AlarmLocale.valueOf(this[DeviceTokenTable.language]),
    platform = DevicePlatform.valueOf(this[DeviceTokenTable.platform]),
)

/** 도메인 → 기기 토큰 행. id와 시각은 DB가 채운다. */
fun DeviceToken.writeTo(row: UpdateBuilder<*>) {
    row[DeviceTokenTable.deviceId] = deviceId
    row[DeviceTokenTable.memberId] = memberId
    row[DeviceTokenTable.fcmToken] = fcmToken
    row[DeviceTokenTable.language] = language.name
    row[DeviceTokenTable.platform] = platform.name
}

/** 수신 설정 행 → 도메인. */
fun ResultRow.toNotificationPreference(): NotificationPreference = NotificationPreference(
    id = this[NotificationPreferenceTable.id],
    memberId = this[NotificationPreferenceTable.memberId],
    category = NotificationCategory.valueOf(this[NotificationPreferenceTable.category]),
    enabled = this[NotificationPreferenceTable.enabled],
)

/** 도메인 → 수신 설정 행. */
fun NotificationPreference.writeTo(row: UpdateBuilder<*>) {
    row[NotificationPreferenceTable.memberId] = memberId
    row[NotificationPreferenceTable.category] = category.name
    row[NotificationPreferenceTable.enabled] = enabled
}

/** 알림 문구 행 → 도메인(읽기 전용이라 반대 방향은 없다). */
fun ResultRow.toNotificationMessage(): NotificationMessage = NotificationMessage.of(
    type = NotificationType.valueOf(this[NotificationMessageTable.notificationType]),
    locale = AlarmLocale.valueOf(this[NotificationMessageTable.locale]),
    title = this[NotificationMessageTable.title],
    body = this[NotificationMessageTable.body],
)
