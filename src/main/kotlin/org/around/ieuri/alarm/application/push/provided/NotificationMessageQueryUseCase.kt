package org.around.ieuri.alarm.application.push.provided

import org.around.ieuri.alarm.domain.device.AlarmLocale
import org.around.ieuri.alarm.domain.push.NotificationMessage
import org.around.ieuri.alarm.domain.push.NotificationType

/** 알림 문구 조회(SPEC-0028 R13). */
interface NotificationMessageQueryUseCase {
    /** 이 종류의 언어별 문구. 문구가 없는 언어는 키가 없다. */
    fun messagesOf(type: NotificationType): Map<AlarmLocale, NotificationMessage>
}
