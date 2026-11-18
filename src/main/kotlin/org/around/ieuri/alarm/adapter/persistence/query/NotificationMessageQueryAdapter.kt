package org.around.ieuri.alarm.adapter.persistence.query

import org.around.ieuri.alarm.adapter.persistence.mapper.toNotificationMessage
import org.around.ieuri.alarm.adapter.persistence.table.NotificationMessageTable
import org.around.ieuri.alarm.application.push.required.NotificationMessageQueryPort
import org.around.ieuri.alarm.domain.push.NotificationMessage
import org.around.ieuri.alarm.domain.push.NotificationType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

@Component
class NotificationMessageQueryAdapter : NotificationMessageQueryPort {

    override fun findByType(type: NotificationType): List<NotificationMessage> = NotificationMessageTable.selectAll()
        .where { NotificationMessageTable.notificationType eq type.name }
        .map { it.toNotificationMessage() }
}
