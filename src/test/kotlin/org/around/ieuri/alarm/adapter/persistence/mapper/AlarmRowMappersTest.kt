package org.around.ieuri.alarm.adapter.persistence.mapper

import org.around.ieuri.alarm.adapter.persistence.table.DeviceTokenTable
import org.around.ieuri.alarm.adapter.persistence.table.NotificationMessageTable
import org.around.ieuri.alarm.adapter.persistence.table.NotificationPreferenceTable
import org.around.ieuri.alarm.domain.device.AlarmLocale
import org.around.ieuri.alarm.domain.device.DevicePlatform
import org.around.ieuri.alarm.domain.device.DeviceToken
import org.around.ieuri.alarm.domain.preference.NotificationCategory
import org.around.ieuri.alarm.domain.preference.NotificationPreference
import org.around.ieuri.alarm.domain.push.NotificationType
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 행 ↔ 도메인 변환(POL-033). 같은 타입 열끼리 뒤바뀌지 않게 필드마다 다른 값을 준다. 쓴 값은 내부 API인 `values`로만 읽힌다. */
@OptIn(InternalApi::class)
class AlarmRowMappersTest {

    private fun row(table: Table) = InsertStatement<Number>(table)

    @Test
    fun `기기 토큰 행이 도메인으로 옮겨진다`() {
        val token = ResultRow.createAndFillValues(
            mapOf(
                DeviceTokenTable.id to 11L,
                DeviceTokenTable.deviceId to "device-값",
                DeviceTokenTable.memberId to 22L,
                DeviceTokenTable.fcmToken to "fcm-값",
                DeviceTokenTable.language to "JA",
                DeviceTokenTable.platform to "IOS",
            ),
        ).toDeviceToken()

        assertEquals(11L, token.id)
        assertEquals("device-값", token.deviceId)
        assertEquals(22L, token.memberId)
        assertEquals("fcm-값", token.fcmToken)
        assertEquals(AlarmLocale.JA, token.language)
        assertEquals(DevicePlatform.IOS, token.platform)
    }

    @Test
    fun `기기 토큰 도메인이 행으로 옮겨진다 - id는 DB가 채운다`() {
        val row = row(DeviceTokenTable)

        DeviceToken(id = null, deviceId = "device-값", memberId = 33L, fcmToken = "fcm-값", language = AlarmLocale.ZH, platform = DevicePlatform.ANDROID).writeTo(row)

        assertEquals("device-값", row.values[DeviceTokenTable.deviceId])
        assertEquals(33L, row.values[DeviceTokenTable.memberId])
        assertEquals("fcm-값", row.values[DeviceTokenTable.fcmToken])
        assertEquals("ZH", row.values[DeviceTokenTable.language])
        assertEquals("ANDROID", row.values[DeviceTokenTable.platform])
        assertFalse(DeviceTokenTable.id in row.values)
    }

    @Test
    fun `수신 설정 행과 도메인이 서로 옮겨진다`() {
        val read = ResultRow.createAndFillValues(
            mapOf(
                NotificationPreferenceTable.id to 44L,
                NotificationPreferenceTable.memberId to 55L,
                NotificationPreferenceTable.category to "SPOT_QA",
                NotificationPreferenceTable.enabled to false,
            ),
        ).toNotificationPreference()

        assertEquals(44L, read.id)
        assertEquals(55L, read.memberId)
        assertEquals(NotificationCategory.SPOT_QA, read.category)
        assertEquals(false, read.enabled)

        val row = row(NotificationPreferenceTable)
        NotificationPreference(id = null, memberId = 66L, category = NotificationCategory.MESSAGE, enabled = true).writeTo(row)
        assertEquals(66L, row.values[NotificationPreferenceTable.memberId])
        assertEquals("MESSAGE", row.values[NotificationPreferenceTable.category])
        assertEquals(true, row.values[NotificationPreferenceTable.enabled])
        assertFalse(NotificationPreferenceTable.id in row.values)
    }

    /** 문구는 앞뒤 공백을 걷어 읽는다. */
    @Test
    fun `알림 문구 행이 도메인으로 옮겨진다`() {
        val message = ResultRow.createAndFillValues(
            mapOf(
                NotificationMessageTable.id to 77L,
                NotificationMessageTable.notificationType to "SPOT_QA_REPLY",
                NotificationMessageTable.locale to "EN",
                NotificationMessageTable.title to "  제목-값  ",
                NotificationMessageTable.body to "본문-값",
            ),
        ).toNotificationMessage()

        assertEquals(NotificationType.SPOT_QA_REPLY, message.type)
        assertEquals(AlarmLocale.EN, message.locale)
        assertEquals("제목-값", message.title)
        assertEquals("본문-값", message.body)
    }
}
