package org.around.ieuri.alarm.application.preference.provided.dto

import org.around.ieuri.alarm.domain.preference.NotificationCategory

/** 카테고리 1건의 수신 설정 읽기 모델(SPEC-0028 R19). */
data class NotificationPreferenceView(
    val category: NotificationCategory,
    val enabled: Boolean,
)
