package org.around.ieuri.alarm.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.alarm.application.command.DeleteDeviceTokenCommand
import org.around.ieuri.alarm.application.command.RegisterDeviceTokenCommand
import org.around.ieuri.alarm.application.command.UpdateNotificationPreferenceCommand
import org.around.ieuri.alarm.application.query.NotificationPreferenceView
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.DevicePlatform
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.around.ieuri.common.graphql.generated.types.DeleteDeviceTokenInput
import org.around.ieuri.common.graphql.generated.types.RegisterDeviceTokenInput
import org.around.ieuri.common.graphql.generated.types.UpdateNotificationPreferenceInput
import org.around.ieuri.common.graphql.generated.types.DevicePlatform as GqlDevicePlatform
import org.around.ieuri.common.graphql.generated.types.NotificationCategory as GqlNotificationCategory
import org.around.ieuri.common.graphql.generated.types.NotificationPreference as GqlNotificationPreference

/** GraphQL 입력 → Command(POL-033). memberId는 인증 컨텍스트에서 오므로 싣지 않는다. */
fun RegisterDeviceTokenInput.toCommand(): RegisterDeviceTokenCommand = RegisterDeviceTokenCommand(
    deviceId = deviceId,
    fcmToken = fcmToken,
    // 지원하지 않는 언어는 EN으로 떨어진다(AC20.3).
    language = AlarmLocale.ofOrFallback(language.name),
    platform = platform.toDomain(),
)

fun DeleteDeviceTokenInput.toCommand(): DeleteDeviceTokenCommand = DeleteDeviceTokenCommand(
    deviceId = deviceId,
)

fun UpdateNotificationPreferenceInput.toCommand(): UpdateNotificationPreferenceCommand = UpdateNotificationPreferenceCommand(
    category = category.toDomain(),
    enabled = enabled,
)

fun GqlDevicePlatform.toDomain(): DevicePlatform = DevicePlatform.valueOf(name)

fun GqlNotificationCategory.toDomain(): NotificationCategory = NotificationCategory.valueOf(name)

/** 읽기 모델 → GraphQL 응답. */
fun NotificationPreferenceView.toGql(): GqlNotificationPreference = GqlNotificationPreference(
    category = GqlNotificationCategory.valueOf(category.name),
    enabled = enabled,
)
