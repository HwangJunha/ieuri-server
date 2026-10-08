package org.around.ieuri.alarm.adapter.persistence

import org.around.ieuri.alarm.adapter.persistence.mapper.toNotificationMessage
import org.around.ieuri.alarm.adapter.persistence.table.NotificationMessageTable
import org.around.ieuri.alarm.application.push.required.NotificationMessageRepository
import org.around.ieuri.alarm.domain.push.NotificationMessage
import org.around.ieuri.alarm.domain.push.NotificationType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository

@Repository
class NotificationMessagePersistenceAdapter : NotificationMessageRepository {

    override fun findByType(type: NotificationType): List<NotificationMessage> = NotificationMessageTable.selectAll()
        .where { NotificationMessageTable.notificationType eq type.name }
        .map { it.toNotificationMessage() }
}
