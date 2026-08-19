package org.around.ieuri.alarm.application.port.`in`.query

import org.around.ieuri.alarm.application.query.NotificationPreferenceView

/** 수신 설정 조회(SPEC-0028 R19). */
interface NotificationPreferenceQueryUseCase {
    /**
     * 호출자의 카테고리별 설정. **항상 두 항목을 돌려준다** — 저장된 행이 없는 회원도
     * 켜짐으로 채워서 온다(AC19.1).
     */
    fun findByMemberId(memberId: Long): List<NotificationPreferenceView>
}
