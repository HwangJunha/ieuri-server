package org.around.ieuri.alarm.adapter.persistence.query

import org.around.ieuri.alarm.adapter.persistence.mapper.toDeviceTokenView
import org.around.ieuri.alarm.adapter.persistence.table.DeviceTokenTable
import org.around.ieuri.alarm.application.device.provided.dto.DeviceTokenView
import org.around.ieuri.alarm.application.device.required.DeviceTokenQueryPort
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

@Component
class DeviceTokenQueryAdapter : DeviceTokenQueryPort {

    override fun findByMemberId(memberId: Long): List<DeviceTokenView> = DeviceTokenTable
        .select(DeviceTokenTable.deviceId, DeviceTokenTable.fcmToken, DeviceTokenTable.language, DeviceTokenTable.platform)
        .where { DeviceTokenTable.memberId eq memberId }
        .map { it.toDeviceTokenView() }
}
