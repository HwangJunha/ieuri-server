package org.around.ieuri.alarm.adapter.persistence

import org.around.ieuri.alarm.adapter.persistence.mapper.toDeviceToken
import org.around.ieuri.alarm.adapter.persistence.mapper.writeTo
import org.around.ieuri.alarm.adapter.persistence.table.DeviceTokenTable
import org.around.ieuri.alarm.application.device.required.DeviceTokenRepository
import org.around.ieuri.alarm.domain.device.DeviceToken
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class DeviceTokenPersistenceAdapter : DeviceTokenRepository {

    override fun findByDeviceId(deviceId: String): DeviceToken? = DeviceTokenTable.selectAll()
        .where { DeviceTokenTable.deviceId eq deviceId }
        .singleOrNull()
        ?.toDeviceToken()

    override fun findByMemberId(memberId: Long): List<DeviceToken> = DeviceTokenTable.selectAll()
        .where { DeviceTokenTable.memberId eq memberId }
        .map { it.toDeviceToken() }

    /** 값이 같으면 행을 고치지 않는다 — `updated_at`이 "바뀐 시각"으로 남는다. */
    override fun save(deviceToken: DeviceToken): DeviceToken {
        val id = deviceToken.id
            ?: return deviceToken.copy(id = DeviceTokenTable.insertReturning(listOf(DeviceTokenTable.id)) { deviceToken.writeTo(it) }.single()[DeviceTokenTable.id])
        DeviceTokenTable.update({
            (DeviceTokenTable.id eq id) and (
                (DeviceTokenTable.memberId neq deviceToken.memberId) or
                    (DeviceTokenTable.fcmToken neq deviceToken.fcmToken) or
                    (DeviceTokenTable.language neq deviceToken.language.name) or
                    (DeviceTokenTable.platform neq deviceToken.platform.name)
                )
        }) {
            deviceToken.writeTo(it)
            it[DeviceTokenTable.updatedAt] = CurrentTimestampWithTimeZone
        }
        return deviceToken
    }

    override fun deleteByDeviceId(deviceId: String): Long = DeviceTokenTable.deleteWhere { DeviceTokenTable.deviceId eq deviceId }.toLong()

    override fun deleteByMemberId(memberId: Long): Long = DeviceTokenTable.deleteWhere { DeviceTokenTable.memberId eq memberId }.toLong()

    override fun deleteByDeviceIds(deviceIds: Collection<String>): Long {
        if (deviceIds.isEmpty()) return 0
        return DeviceTokenTable.deleteWhere { DeviceTokenTable.deviceId inList deviceIds }.toLong()
    }
}
