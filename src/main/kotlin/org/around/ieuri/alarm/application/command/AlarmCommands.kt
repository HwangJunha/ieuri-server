package org.around.ieuri.alarm.application.command

import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.DevicePlatform
import org.around.ieuri.alarm.domain.vo.NotificationCategory

/**
 * 기기 토큰 등록(SPEC-0028 R1). **업서트다** — 같은 [deviceId]로 다시 부르면 행을 늘리지
 * 않고 토큰·언어·소유 회원을 덮어쓴다.
 *
 * **memberId를 싣지 않는다** — 호출자 신원은 인증 컨텍스트에서 오며 입력이 정하지 않는다.
 */
data class RegisterDeviceTokenCommand(
    val deviceId: String,
    val fcmToken: String,
    val language: AlarmLocale,
    val platform: DevicePlatform,
)

/** 기기 토큰 해제(R2). 이미 없어도 성공이다(AC2.2). */
data class DeleteDeviceTokenCommand(
    val deviceId: String,
)

/** 카테고리별 수신 설정 변경(R19). 같은 값으로 다시 불러도 성공이다(AC19.5). */
data class UpdateNotificationPreferenceCommand(
    val category: NotificationCategory,
    val enabled: Boolean,
)
