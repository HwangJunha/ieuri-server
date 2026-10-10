package org.around.ieuri.alarm.application.device.provided.dto

import org.around.ieuri.alarm.domain.device.AlarmLocale
import org.around.ieuri.alarm.domain.device.DevicePlatform

/** 기기 토큰 등록(SPEC-0028 R1). 같은 [deviceId]면 토큰·언어·소유 회원을 덮어쓰는 업서트다. */
data class RegisterDeviceTokenCommand(
    val deviceId: String,
    val fcmToken: String,
    val language: AlarmLocale,
    val platform: DevicePlatform,
)
