package org.around.ieuri.alarm.adapter.persistence.query

import org.around.ieuri.alarm.adapter.persistence.table.NotificationPreferenceTable
import org.around.ieuri.alarm.application.preference.required.NotificationPreferenceQueryPort
import org.around.ieuri.alarm.domain.preference.NotificationCategory
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

@Component
class NotificationPreferenceQueryAdapter : NotificationPreferenceQueryPort {

    override fun findEnabledByCategory(memberId: Long): Map<NotificationCategory, Boolean> = NotificationPreferenceTable
        .select(NotificationPreferenceTable.category, NotificationPreferenceTable.enabled)
        .where { NotificationPreferenceTable.memberId eq memberId }
        .associate { NotificationCategory.valueOf(it[NotificationPreferenceTable.category]) to it[NotificationPreferenceTable.enabled] }

    override fun findEnabled(memberId: Long, category: NotificationCategory): Boolean? = NotificationPreferenceTable
        .select(NotificationPreferenceTable.enabled)
        .where { (NotificationPreferenceTable.memberId eq memberId) and (NotificationPreferenceTable.category eq category.name) }
        .singleOrNull()
        ?.get(NotificationPreferenceTable.enabled)
}
