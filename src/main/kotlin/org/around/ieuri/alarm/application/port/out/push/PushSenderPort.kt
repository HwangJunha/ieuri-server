package org.around.ieuri.alarm.application.port.out.push

import org.around.ieuri.alarm.domain.vo.PushNotification

/** 벤더 중립 푸시 발송 포트(SPEC-0028). 오류 코드 해석이 끝난 결과를 돌려준다. */
interface PushSenderPort {
    /**
     * 각 기기로 알림을 보낸다. 일부가 실패해도 나머지는 나간다(AC4.3).
     * - 예외를 던지지 않고 재시도하지 않는다(R12·AC12.3). 전면 실패는 [PushSendResult.NONE]이다.
     */
    fun send(notification: PushNotification, targets: List<PushTarget>): PushSendResult
}

/** 발송 대상 기기 하나. */
data class PushTarget(
    val deviceId: String,
    val fcmToken: String,
)

/** 발송 결과. */
data class PushSendResult(
    val successCount: Int,
    /** FCM이 무효라고 알려 준 기기만 담긴다(R4). 일시 오류·설정 오류는 담기지 않는다. */
    val invalidDeviceIds: List<String>,
) {
    companion object {
        /** 보낼 대상이 없거나 발송 자체가 성립하지 않은 경우. 지울 토큰도 없다. */
        val NONE = PushSendResult(successCount = 0, invalidDeviceIds = emptyList())
    }
}
