package org.around.ieuri.alarm.adapter.persistence

import org.around.ieuri.alarm.adapter.persistence.mapper.toNotificationPreference
import org.around.ieuri.alarm.adapter.persistence.mapper.writeTo
import org.around.ieuri.alarm.adapter.persistence.table.NotificationPreferenceTable
import org.around.ieuri.alarm.application.preference.required.NotificationPreferenceRepository
import org.around.ieuri.alarm.domain.preference.NotificationCategory
import org.around.ieuri.alarm.domain.preference.NotificationPreference
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class NotificationPreferencePersistenceAdapter : NotificationPreferenceRepository {

    override fun findByMemberId(memberId: Long): List<NotificationPreference> = NotificationPreferenceTable.selectAll()
        .where { NotificationPreferenceTable.memberId eq memberId }
        .map { it.toNotificationPreference() }

    override fun findByMemberIdAndCategory(
        memberId: Long,
        category: NotificationCategory,
    ): NotificationPreference? = NotificationPreferenceTable.selectAll()
        .where { (NotificationPreferenceTable.memberId eq memberId) and (NotificationPreferenceTable.category eq category.name) }
        .singleOrNull()
        ?.toNotificationPreference()

    /** 값이 같으면 행을 고치지 않는다 — `updated_at`이 "바뀐 시각"으로 남는다. */
    override fun save(preference: NotificationPreference) {
        val id = preference.id
        if (id == null) {
            NotificationPreferenceTable.insert { preference.writeTo(it) }
            return
        }
        NotificationPreferenceTable.update({ (NotificationPreferenceTable.id eq id) and (NotificationPreferenceTable.enabled neq preference.enabled) }) {
            it[NotificationPreferenceTable.enabled] = preference.enabled
            it[NotificationPreferenceTable.updatedAt] = CurrentTimestampWithTimeZone
        }
    }

    override fun deleteByMemberId(memberId: Long): Long = NotificationPreferenceTable.deleteWhere { NotificationPreferenceTable.memberId eq memberId }.toLong()
}
