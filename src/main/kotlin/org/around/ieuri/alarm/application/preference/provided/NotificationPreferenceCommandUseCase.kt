package org.around.ieuri.alarm.application.preference.provided

import org.around.ieuri.alarm.application.preference.provided.dto.NotificationPreferenceView
import org.around.ieuri.alarm.application.preference.provided.dto.UpdateNotificationPreferenceCommand

/** 카테고리별 수신 설정 변경·파기(SPEC-0028 R3·R19). */
interface NotificationPreferenceCommandUseCase {
    /** 반영 후 두 카테고리 전부를 돌려준다(AC19.3). */
    fun update(
        command: UpdateNotificationPreferenceCommand,
        memberId: Long,
    ): List<NotificationPreferenceView>

    /** 탈퇴 회원의 수신 설정을 지운다(R3). `AFTER_COMMIT` 리스너가 부른다. */
    fun eraseMemberPreferences(memberId: Long)
}
