package org.around.ieuri.alarm.application.preference.required

import org.around.ieuri.alarm.domain.preference.NotificationCategory
import org.around.ieuri.alarm.domain.preference.NotificationPreference

/** 수신 설정 조회(SPEC-0028 R10·R19). 저장된 행만 읽는다 — 행이 없을 때의 값은 도메인이 정한다. */
interface NotificationPreferenceQueryPort {
    fun findByMemberId(memberId: Long): List<NotificationPreference>

    /** 저장된 값. 행이 없으면 null이다. */
    fun findEnabled(memberId: Long, category: NotificationCategory): Boolean?
}
