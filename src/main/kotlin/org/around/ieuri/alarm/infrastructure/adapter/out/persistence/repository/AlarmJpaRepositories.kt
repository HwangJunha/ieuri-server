package org.around.ieuri.alarm.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.entity.DeviceTokenEntity
import org.around.ieuri.alarm.infrastructure.adapter.out.persistence.entity.NotificationPreferenceEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DeviceTokenJpaRepository : JpaRepository<DeviceTokenEntity, Long> {
    fun findByDeviceId(deviceId: String): DeviceTokenEntity?

    fun findByMemberId(memberId: Long): List<DeviceTokenEntity>

    fun deleteByDeviceId(deviceId: String): Long

    /** 탈퇴 연쇄 삭제(R3). */
    fun deleteByMemberId(memberId: Long): Long

    /** 무효 토큰 제거(R4). */
    fun deleteByDeviceIdIn(deviceIds: Collection<String>): Long
}

interface NotificationPreferenceJpaRepository : JpaRepository<NotificationPreferenceEntity, Long> {
    fun findByMemberId(memberId: Long): List<NotificationPreferenceEntity>

    fun findByMemberIdAndCategory(
        memberId: Long,
        category: NotificationCategory,
    ): NotificationPreferenceEntity?

    /** 탈퇴 연쇄 삭제(R3). */
    fun deleteByMemberId(memberId: Long): Long
}
