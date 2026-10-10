package org.around.ieuri.alarm.application.push.required.dto

/** 발송 대상 기기 하나. */
data class PushTarget(
    val deviceId: String,
    val fcmToken: String,
)
