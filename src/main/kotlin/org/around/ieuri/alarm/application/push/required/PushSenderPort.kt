package org.around.ieuri.alarm.application.push.required

import org.around.ieuri.alarm.application.push.required.dto.PushSendResult
import org.around.ieuri.alarm.application.push.required.dto.PushTarget
import org.around.ieuri.alarm.domain.push.PushNotification

/** 벤더 중립 푸시 발송 포트(SPEC-0028). 오류 코드 해석이 끝난 결과를 돌려준다. */
interface PushSenderPort {
    /**
     * 각 기기로 알림을 보낸다. 일부가 실패해도 나머지는 나간다(AC4.3).
     * - 예외를 던지지 않고 재시도하지 않는다(R12·AC12.3). 전면 실패는 [PushSendResult.NONE]이다.
     */
    fun send(notification: PushNotification, targets: List<PushTarget>): PushSendResult
}
