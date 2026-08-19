package org.around.ieuri.alarm.domain.vo

/**
 * 알림 종류(SPEC-0028 R11).
 *
 * **[NotificationCategory]보다 잘다.** 카테고리는 사용자가 끄고 켜는 단위라 답변과 답글이
 * 하나로 묶이지만(`SPOT_QA`), 문구는 둘이 달라야 한다. 그래서 수신 설정은 카테고리로,
 * 제목 문구 조회는 이 값으로 한다.
 *
 * 이름이 곧 FCM 데이터 메시지의 `type` 값이다 — 클라이언트가 이 문자열로 목적지를 가른다.
 */
enum class NotificationType {
    DM,
    SPOT_QA_ANSWER,
    SPOT_QA_REPLY,
}
