package org.around.ieuri.alarm.application.preference.provided.dto

import org.around.ieuri.alarm.domain.preference.NotificationCategory

/** 카테고리별 수신 설정 변경(R19). 같은 값으로 다시 불러도 성공이다(AC19.5). */
data class UpdateNotificationPreferenceCommand(
    val category: NotificationCategory,
    val enabled: Boolean,
)
