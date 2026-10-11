package org.around.ieuri.alarm.application.push.required

import org.around.ieuri.alarm.domain.push.NotificationMessage
import org.around.ieuri.alarm.domain.push.NotificationType

/** 알림 문구 조회(SPEC-0028 R13). 읽기 전용이며 값은 마이그레이션·운영자가 넣는다. */
interface NotificationMessageQueryPort {
    /** 한 종류의 모든 언어 문구. */
    fun findByType(type: NotificationType): List<NotificationMessage>
}
