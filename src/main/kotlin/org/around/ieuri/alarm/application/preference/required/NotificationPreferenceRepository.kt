package org.around.ieuri.alarm.application.preference.required

import org.around.ieuri.alarm.domain.preference.NotificationCategory
import org.around.ieuri.alarm.domain.preference.NotificationPreference

/** 수신 설정 저장(SPEC-0028 R19). 행이 없으면 켜짐이다(AC10.3). */
interface NotificationPreferenceRepository {
    fun findByMemberId(memberId: Long): List<NotificationPreference>

    fun findByMemberIdAndCategory(
        memberId: Long,
        category: NotificationCategory,
    ): NotificationPreference?

    /** id가 없으면 넣는다. 있으면 값이 바뀐 경우에만 고친다(`updated_at` 포함). */
    fun save(preference: NotificationPreference)

    /** 지운 건수. */
    fun deleteByMemberId(memberId: Long): Long
}
