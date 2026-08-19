package org.around.ieuri.alarm.infrastructure.adapter.out.persistence

import org.around.ieuri.alarm.application.port.out.persistence.DeviceTokenRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationMessageRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationPreferenceRepositoryPort
import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.around.ieuri.alarm.domain.aggregate.NotificationMessage
import org.around.ieuri.alarm.domain.aggregate.NotificationPreference
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.around.ieuri.alarm.domain.vo.NotificationType
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.repository.DeviceTokenJpaRepository
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.repository.NotificationMessageJpaRepository
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.repository.NotificationPreferenceJpaRepository
import org.springframework.stereotype.Component

@Component
class DeviceTokenPersistenceAdapter(
    private val repository: DeviceTokenJpaRepository,
) : DeviceTokenRepositoryPort {

    override fun findByDeviceId(deviceId: String): DeviceToken? =
        repository.findByDeviceId(deviceId)?.toDomain()

    override fun findByMemberId(memberId: Long): List<DeviceToken> =
        repository.findByMemberId(memberId).map { it.toDomain() }

    override fun save(deviceToken: DeviceToken): DeviceToken =
        repository.save(deviceToken.toNewEntity()).toDomain()

    override fun update(deviceToken: DeviceToken) {
        val entity = repository.findByDeviceId(deviceToken.deviceId) ?: return
        entity.memberId = deviceToken.memberId
        entity.fcmToken = deviceToken.fcmToken
        entity.language = deviceToken.language
        entity.platform = deviceToken.platform
        // dirty-check로 UPDATE.
    }

    override fun deleteByDeviceId(deviceId: String): Long = repository.deleteByDeviceId(deviceId)

    override fun deleteByMemberId(memberId: Long): Long = repository.deleteByMemberId(memberId)

    override fun deleteByDeviceIds(deviceIds: Collection<String>): Long {
        if (deviceIds.isEmpty()) return 0
        return repository.deleteByDeviceIdIn(deviceIds)
    }
}

@Component
class NotificationMessagePersistenceAdapter(
    private val repository: NotificationMessageJpaRepository,
) : NotificationMessageRepositoryPort {

    override fun findByType(type: NotificationType): List<NotificationMessage> =
        repository.findByNotificationType(type).map { it.toDomain() }
}

@Component
class NotificationPreferencePersistenceAdapter(
    private val repository: NotificationPreferenceJpaRepository,
) : NotificationPreferenceRepositoryPort {

    override fun findByMemberId(memberId: Long): List<NotificationPreference> =
        repository.findByMemberId(memberId).map { it.toDomain() }

    override fun findByMemberIdAndCategory(
        memberId: Long,
        category: NotificationCategory,
    ): NotificationPreference? =
        repository.findByMemberIdAndCategory(memberId, category)?.toDomain()

    override fun save(preference: NotificationPreference): NotificationPreference =
        repository.save(preference.toNewEntity()).toDomain()

    override fun update(preference: NotificationPreference) {
        val entity = repository.findByMemberIdAndCategory(preference.memberId, preference.category)
            ?: return
        entity.enabled = preference.enabled
        // dirty-check로 UPDATE.
    }

    override fun deleteByMemberId(memberId: Long): Long = repository.deleteByMemberId(memberId)
}
