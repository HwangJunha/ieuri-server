package org.around.ieuri.alarm.domain.vo

/**
 * 기기 하나로 나갈 알림 한 건(SPEC-0028 R11·R13).
 *
 * **본문이 없다.** 사용자가 쓴 메시지·답변 내용을 푸시에 싣지 않는다 — 잠금화면에 그대로
 * 뜨면 화면을 켜지 않아도 대화가 읽히기 때문이다. 알림은 "무언가 도착했다"까지만 알리고,
 * 내용은 사용자가 앱을 열어 확인한다.
 *
 * 그래서 [title]이 알림의 유일한 내용이며, 수신 기기의 언어로 번역된 문구다.
 */
data class PushNotification(
    val title: String,
    val destination: PushDestination,
)
