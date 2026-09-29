package org.around.ieuri.alarm.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.around.ieuri.alarm.domain.aggregate.NotificationMessage
import org.around.ieuri.alarm.domain.aggregate.NotificationPreference
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.entity.DeviceTokenEntity
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.entity.NotificationMessageEntity
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.entity.NotificationPreferenceEntity

/** 기기 토큰 엔티티를 도메인 모델로 옮긴다. */
fun DeviceTokenEntity.toDomain(): DeviceToken = DeviceToken.restore(
    id = requireNotNull(id) { "저장된 기기 토큰에는 id가 있어야 한다." },
    deviceId = deviceId,
    memberId = memberId,
    fcmToken = fcmToken,
    language = language,
    platform = platform,
    createdAt = requireNotNull(createdAt) { "저장된 기기 토큰에는 createdAt이 있어야 한다." },
)

/** 도메인 모델을 새 엔티티로 옮긴다. id와 시각은 DB가 채운다. */
fun DeviceToken.toNewEntity(): DeviceTokenEntity = DeviceTokenEntity(
    deviceId = deviceId,
    memberId = memberId,
    fcmToken = fcmToken,
    language = language,
    platform = platform,
)

/** 알림 문구 엔티티를 도메인 모델로 옮긴다(읽기 전용이라 반대 방향은 없다). */
fun NotificationMessageEntity.toDomain(): NotificationMessage = NotificationMessage.restore(
    id = requireNotNull(id) { "저장된 알림 문구에는 id가 있어야 한다." },
    type = notificationType,
    locale = locale,
    title = title,
    body = body,
)

/** 수신 설정 엔티티를 도메인 모델로 옮긴다. */
fun NotificationPreferenceEntity.toDomain(): NotificationPreference = NotificationPreference.restore(
    id = requireNotNull(id) { "저장된 수신 설정에는 id가 있어야 한다." },
    memberId = memberId,
    category = category,
    enabled = enabled,
)

/** 도메인 모델을 새 엔티티로 옮긴다. */
fun NotificationPreference.toNewEntity(): NotificationPreferenceEntity = NotificationPreferenceEntity(
    memberId = memberId,
    category = category,
    enabled = enabled,
)
