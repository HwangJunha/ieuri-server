package org.around.ieuri.alarm.adapter.persistence.query

import org.around.ieuri.alarm.adapter.persistence.mapper.toNotificationPreference
import org.around.ieuri.alarm.adapter.persistence.table.NotificationPreferenceTable
import org.around.ieuri.alarm.application.preference.required.NotificationPreferenceQueryPort
import org.around.ieuri.alarm.domain.preference.NotificationCategory
import org.around.ieuri.alarm.domain.preference.NotificationPreference
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

@Component
class NotificationPreferenceQueryAdapter : NotificationPreferenceQueryPort {

    override fun findByMemberId(memberId: Long): List<NotificationPreference> = NotificationPreferenceTable.selectAll()
        .where { NotificationPreferenceTable.memberId eq memberId }
        .map { it.toNotificationPreference() }

    override fun findEnabled(memberId: Long, category: NotificationCategory): Boolean? = NotificationPreferenceTable
        .select(NotificationPreferenceTable.enabled)
        .where { (NotificationPreferenceTable.memberId eq memberId) and (NotificationPreferenceTable.category eq category.name) }
        .singleOrNull()
        ?.get(NotificationPreferenceTable.enabled)
}
