package org.around.ieuri.alarm.infrastructure.mapper

import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.around.ieuri.alarm.domain.aggregate.NotificationPreference
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.DevicePlatform
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.around.ieuri.alarm.domain.vo.NotificationType
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.entity.DeviceTokenEntity
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.entity.NotificationMessageEntity
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.entity.NotificationPreferenceEntity
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.mapper.toNewEntity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 엔티티 ↔ 도메인 변환(POL-033).
 *
 * 필드마다 **서로 다른 값**을 준다 — `deviceId`와 `fcmToken`이 둘 다 String이라 뒤바뀌어도
 * 컴파일이 통과한다.
 */
class AlarmMapperTest {

    private val createdAt = Instant.parse("2026-08-19T01:02:03Z")

    @Test
    fun `기기 토큰 엔티티가 도메인으로 옮겨진다`() {
        val entity = DeviceTokenEntity(
            id = 11L,
            deviceId = "device-값",
            memberId = 22L,
            fcmToken = "fcm-값",
            language = AlarmLocale.JA,
            platform = DevicePlatform.IOS,
            createdAt = createdAt,
            updatedAt = Instant.parse("2026-08-19T09:09:09Z"),
        )

        val domain = entity.toDomain()

        assertEquals(11L, domain.id)
        assertEquals("device-값", domain.deviceId)
        assertEquals(22L, domain.memberId)
        assertEquals("fcm-값", domain.fcmToken)
        assertEquals(AlarmLocale.JA, domain.language)
        assertEquals(DevicePlatform.IOS, domain.platform)
        assertEquals(createdAt, domain.createdAt)
    }

    @Test
    fun `기기 토큰 도메인이 새 엔티티로 옮겨진다`() {
        val domain = DeviceToken.create(
            deviceId = "device-값",
            memberId = 33L,
            fcmToken = "fcm-값",
            language = AlarmLocale.ZH,
            platform = DevicePlatform.ANDROID,
        )

        val entity = domain.toNewEntity()

        assertEquals("device-값", entity.deviceId)
        assertEquals(33L, entity.memberId)
        assertEquals("fcm-값", entity.fcmToken)
        assertEquals(AlarmLocale.ZH, entity.language)
        assertEquals(DevicePlatform.ANDROID, entity.platform)
        // id와 시각은 DB가 채운다.
        assertNull(entity.id)
        assertNull(entity.createdAt)
    }

    @Test
    fun `수신 설정 엔티티가 도메인으로 옮겨진다`() {
        val entity = NotificationPreferenceEntity(
            id = 44L,
            memberId = 55L,
            category = NotificationCategory.SPOT_QA,
            enabled = false,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        val domain = entity.toDomain()

        assertEquals(44L, domain.id)
        assertEquals(55L, domain.memberId)
        assertEquals(NotificationCategory.SPOT_QA, domain.category)
        assertEquals(false, domain.enabled)
    }

    /** 종류와 언어가 둘 다 enum이라 뒤바뀌면 컴파일이 통과한다 — 값을 다르게 준다. */
    @Test
    fun `알림 문구 엔티티가 도메인으로 옮겨진다`() {
        val entity = NotificationMessageEntity(
            id = 77L,
            notificationType = NotificationType.SPOT_QA_REPLY,
            locale = AlarmLocale.ZH,
            title = "제목값",
            body = "본문값",
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        val domain = entity.toDomain()

        assertEquals(77L, domain.id)
        assertEquals(NotificationType.SPOT_QA_REPLY, domain.type)
        assertEquals(AlarmLocale.ZH, domain.locale)
        assertEquals("제목값", domain.title)
        assertEquals("본문값", domain.body)
    }

    @Test
    fun `수신 설정 도메인이 새 엔티티로 옮겨진다`() {
        val domain = NotificationPreference.create(66L, NotificationCategory.MESSAGE, true)

        val entity = domain.toNewEntity()

        assertEquals(66L, entity.memberId)
        assertEquals(NotificationCategory.MESSAGE, entity.category)
        assertEquals(true, entity.enabled)
        assertNull(entity.id)
    }
}
