package org.around.ieuri.alarm.application.preference.required

import org.around.ieuri.alarm.domain.preference.NotificationCategory

/** 수신 설정 조회(SPEC-0028 R10·R19). 저장된 행만 읽는다 — 행이 없을 때의 값은 도메인이 정한다. */
interface NotificationPreferenceQueryPort {
    /** 저장된 카테고리만 담는다. 설정을 바꾼 적 없는 카테고리는 키가 없다. */
    fun findEnabledByCategory(memberId: Long): Map<NotificationCategory, Boolean>

    /** 저장된 값. 행이 없으면 null이다. */
    fun findEnabled(memberId: Long, category: NotificationCategory): Boolean?
}
