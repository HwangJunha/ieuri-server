package org.around.ieuri.alarm.application.push.required.dto

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
