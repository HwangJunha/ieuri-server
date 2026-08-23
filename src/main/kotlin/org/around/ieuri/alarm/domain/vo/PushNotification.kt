package org.around.ieuri.alarm.domain.vo

/**
 * 기기 하나로 나갈 알림 한 건(SPEC-0028 R11·R13).
 *
 * **[body]는 사용자가 쓴 내용이 아니다.** 메시지·답변 본문은 여전히 알림에 싣지 않는다 —
 * 잠금화면에 대화가 읽히지 않아야 하기 때문이다. 여기 들어가는 것은 서버가 정한 고정
 * 안내 문구("앱에서 확인해 보세요")이며 [title]과 함께 DB에서 온다.
 *
 * 본문이 필요한 이유는 표시 규칙이다 — **iOS(APNs)는 본문이 없는 알림을 표시하지 않는다.**
 * 제목만 보내면 FCM은 정상 접수하고 기기에서 조용히 버려져, 오류 없이 iOS에만 알림이
 * 안 오는 형태가 된다.
 */
data class PushNotification(
    val title: String,
    val body: String,
    val destination: PushDestination,
)
