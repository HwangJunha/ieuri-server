package org.around.ieuri.alarm.infrastructure.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import org.around.ieuri.alarm.application.query.NotificationPreferenceView
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.DevicePlatform
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.around.ieuri.alarm.infrastructure.adapter.`in`.graphql.mapper.toCommand
import org.around.ieuri.alarm.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.common.graphql.generated.types.DeleteDeviceTokenInput
import org.around.ieuri.common.graphql.generated.types.Locale as GqlLocale
import org.around.ieuri.common.graphql.generated.types.RegisterDeviceTokenInput
import org.around.ieuri.common.graphql.generated.types.UpdateNotificationPreferenceInput
import org.around.ieuri.common.graphql.generated.types.DevicePlatform as GqlDevicePlatform
import org.around.ieuri.common.graphql.generated.types.NotificationCategory as GqlNotificationCategory

/**
 * GraphQL 입력 → Command, 읽기 모델 → 응답 변환(POL-033).
 *
 * `deviceId`와 `fcmToken`이 둘 다 String이라 뒤바뀌어도 컴파일이 통과한다 — 값을 다르게 준다.
 */
class AlarmGraphqlMapperTest {

    @Test
    fun `기기 토큰 등록 입력이 Command로 옮겨진다`() {
        val input = RegisterDeviceTokenInput(
            deviceId = "device-값",
            fcmToken = "fcm-값",
            language = GqlLocale.JA,
            platform = GqlDevicePlatform.IOS,
        )

        val command = input.toCommand()

        assertEquals("device-값", command.deviceId)
        assertEquals("fcm-값", command.fcmToken)
        assertEquals(AlarmLocale.JA, command.language)
        assertEquals(DevicePlatform.IOS, command.platform)
    }

    @Test
    fun `언어 네 가지가 모두 옮겨진다`() {
        GqlLocale.entries.forEach { gql ->
            val command = RegisterDeviceTokenInput(
                deviceId = "d",
                fcmToken = "t",
                language = gql,
                platform = GqlDevicePlatform.ANDROID,
            ).toCommand()

            assertEquals(gql.name, command.language.name, "$gql 이 옮겨지지 않았다")
        }
    }

    @Test
    fun `플랫폼 둘이 모두 옮겨진다`() {
        GqlDevicePlatform.entries.forEach { gql ->
            val command = RegisterDeviceTokenInput(
                deviceId = "d",
                fcmToken = "t",
                language = GqlLocale.EN,
                platform = gql,
            ).toCommand()

            assertEquals(gql.name, command.platform.name, "$gql 이 옮겨지지 않았다")
        }
    }

    @Test
    fun `기기 토큰 삭제 입력이 Command로 옮겨진다`() {
        val command = DeleteDeviceTokenInput(deviceId = "device-값").toCommand()

        assertEquals("device-값", command.deviceId)
    }

    @Test
    fun `수신 설정 변경 입력이 Command로 옮겨진다`() {
        val command = UpdateNotificationPreferenceInput(
            category = GqlNotificationCategory.SPOT_QA,
            enabled = false,
        ).toCommand()

        assertEquals(NotificationCategory.SPOT_QA, command.category)
        assertEquals(false, command.enabled)
    }

    @Test
    fun `카테고리 둘이 모두 옮겨진다`() {
        GqlNotificationCategory.entries.forEach { gql ->
            val command = UpdateNotificationPreferenceInput(category = gql, enabled = true).toCommand()

            assertEquals(gql.name, command.category.name, "$gql 이 옮겨지지 않았다")
        }
    }

    @Test
    fun `수신 설정 읽기 모델이 응답으로 옮겨진다`() {
        val gql = NotificationPreferenceView(NotificationCategory.MESSAGE, enabled = false).toGql()

        assertEquals(GqlNotificationCategory.MESSAGE, gql.category)
        assertEquals(false, gql.enabled)
    }
}
