package org.around.ieuri.alarm.application.preference.provided

import org.around.ieuri.alarm.application.preference.provided.dto.NotificationPreferenceView
import org.around.ieuri.alarm.domain.preference.NotificationCategory

/** 수신 설정 조회(SPEC-0028 R10·R19). */
interface NotificationPreferenceQueryUseCase {
    /** 카테고리별 설정. 항상 두 항목을 돌려주며 저장된 행이 없으면 켜짐이다(AC19.1). */
    fun findByMemberId(memberId: Long): List<NotificationPreferenceView>

    /** 이 카테고리를 받는가. 저장된 행이 없으면 켜진 것으로 본다(AC10.3). */
    fun isEnabled(memberId: Long, category: NotificationCategory): Boolean
}
