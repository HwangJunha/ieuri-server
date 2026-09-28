package org.around.ieuri.alarm.application.port.`in`.query

import org.around.ieuri.alarm.application.query.NotificationPreferenceView

/** 수신 설정 조회(SPEC-0028 R19). */
interface NotificationPreferenceQueryUseCase {
    /** 카테고리별 설정. 항상 두 항목을 돌려주며 저장된 행이 없으면 켜짐이다(AC19.1). */
    fun findByMemberId(memberId: Long): List<NotificationPreferenceView>
}
