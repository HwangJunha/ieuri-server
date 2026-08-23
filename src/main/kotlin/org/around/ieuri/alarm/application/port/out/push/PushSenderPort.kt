package org.around.ieuri.alarm.application.port.out.push

import org.around.ieuri.alarm.domain.vo.PushNotification

/**
 * 푸시 발송 포트(SPEC-0028 §8). **벤더 중립이다** — 구현이 FCM인 것은 어댑터가 안다.
 *
 * 오류 코드 해석은 어댑터의 일이고, 이 포트는 **판정이 끝난 결과**를 돌려준다(설계 문서 §4).
 * 그래야 FCM 응답을 흉내 내지 않고도 삭제 로직을 단위 테스트할 수 있다.
 */
interface PushSenderPort {
    /**
     * [targets]의 각 기기로 알림을 보낸다. 토큰별로 독립이라 일부 실패해도 나머지는 나간다(AC4.3).
     *
     * **예외를 던지지 않는다.** 발송 실패는 원래 작업을 건드리지 않으며(R12) 재시도하지도
     * 않는다(AC12.3). 전면 실패도 "지울 토큰 없음"으로 돌아온다.
     */
    fun send(notification: PushNotification, targets: List<PushTarget>): PushSendResult
}

/** 발송 대상 기기 하나. */
data class PushTarget(
    val deviceId: String,
    val fcmToken: String,
)

/**
 * 발송 결과.
 *
 * [invalidDeviceIds]에는 **FCM이 무효라고 알려 준 토큰만** 담긴다(R4). 서비스 측 일시 오류
 * (`UNAVAILABLE`·`INTERNAL`·`QUOTA_EXCEEDED`)나 설정 오류(`SENDER_ID_MISMATCH`·
 * `THIRD_PARTY_AUTH_ERROR`)는 담기지 않는다 — 그것으로 지우면 장애 한 번에 정상 토큰이
 * 전멸한다.
 */
data class PushSendResult(
    val successCount: Int,
    /** 삭제해야 할 기기. 토큰이 아니라 `deviceId`인 것은 그 사이 로테이션됐을 수 있어서다. */
    val invalidDeviceIds: List<String>,
) {
    companion object {
        /** 보낼 대상이 없거나 발송 자체가 성립하지 않은 경우. 지울 토큰도 없다. */
        val NONE = PushSendResult(successCount = 0, invalidDeviceIds = emptyList())
    }
}
