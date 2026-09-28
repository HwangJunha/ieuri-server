package org.around.ieuri.alarm.domain.vo

/**
 * 알림 종류(SPEC-0028 R11). [NotificationCategory]보다 잘며 문구 조회 키다.
 * - 이름이 곧 FCM 데이터의 `type` 값이다. 바꾸면 클라이언트 라우팅이 깨진다.
 */
enum class NotificationType {
    DM,
    SPOT_QA_ANSWER,
    SPOT_QA_REPLY,
}
