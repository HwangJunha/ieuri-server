package org.around.ieuri.alarm.domain.push

/**
 * 기기 하나로 나갈 알림 한 건(SPEC-0028 R11·R13).
 * - [body]는 사용자가 쓴 내용이 아니라 DB에서 온 고정 안내 문구다.
 * - iOS는 본문 없는 알림을 표시하지 않으므로 [body]를 비우면 안 된다.
 */
data class PushNotification(
    val title: String,
    val body: String,
    val destination: PushDestination,
)
