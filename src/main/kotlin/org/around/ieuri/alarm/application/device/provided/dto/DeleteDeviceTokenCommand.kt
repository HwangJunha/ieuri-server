package org.around.ieuri.alarm.application.device.provided.dto

/** 기기 토큰 해제(R2). 이미 없어도 성공이다(AC2.2). */
data class DeleteDeviceTokenCommand(
    val deviceId: String,
)
