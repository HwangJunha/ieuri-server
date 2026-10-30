package org.around.ieuri.alarm.application.device.provided.dto

import org.around.ieuri.alarm.domain.device.AlarmLocale
import org.around.ieuri.alarm.domain.device.DevicePlatform

/** 발송 대상 기기 1건의 읽기 모델(SPEC-0028 R5). */
data class DeviceTokenView(
    val deviceId: String,
    val fcmToken: String,
    val language: AlarmLocale,
    val platform: DevicePlatform,
)
