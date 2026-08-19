package org.around.ieuri.alarm.infrastructure.adapter.out.push

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.google.firebase.messaging.SendResponse
import org.around.ieuri.alarm.application.port.out.push.PushSendResult
import org.around.ieuri.alarm.application.port.out.push.PushSenderPort
import org.around.ieuri.alarm.application.port.out.push.PushTarget
import org.around.ieuri.alarm.domain.vo.PushNotification
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * FCM HTTP v1 발송 어댑터(SPEC-0028 §4·§8).
 *
 * **오류 코드 해석이 이 클래스의 일이다.** 애플리케이션에는 "지울 기기 목록"만 넘기므로,
 * 삭제 로직은 FCM 응답을 흉내 내지 않고 단위 테스트할 수 있다.
 *
 * `sendEach`는 토큰별로 결과를 돌려주며 입력 순서를 지킨다 — 무효인 것만 골라 지우고
 * 나머지로는 정상 발송된다(AC4.3).
 */
@Component
class FcmPushSender(
    @Autowired(required = false) private val firebaseMessaging: FirebaseMessaging?,
) : PushSenderPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(notification: PushNotification, targets: List<PushTarget>): PushSendResult {
        if (targets.isEmpty()) return PushSendResult.NONE
        val messaging = firebaseMessaging ?: run {
            log.warn("FCM 자격증명이 없어 발송을 건너뛴다: targets={}", targets.size)
            return PushSendResult.NONE
        }

        val messages = targets.map { it.toMessage(notification) }
        return try {
            val response = messaging.sendEach(messages)
            // sendEach는 입력 순서대로 결과를 돌려준다 — 그래서 인덱스로 기기를 되짚을 수 있다.
            val invalid = response.responses.withIndex()
                .filter { (_, each) -> each.shouldDeleteToken() }
                .map { (index, _) -> targets[index].deviceId }

            response.responses.forEachIndexed { index, each -> each.logIfNoteworthy(targets[index]) }
            PushSendResult(successCount = response.successCount, invalidDeviceIds = invalid)
        } catch (e: Exception) {
            // 발송 전체가 실패해도 원래 작업은 건드리지 않고 재시도하지도 않는다(R12·AC12.3).
            // **토큰을 지우지 않는다** — 호출 자체가 실패한 것이라 토큰의 유효성과 무관하다.
            log.warn("FCM 발송에 실패했다: targets={}", targets.size, e)
            PushSendResult.NONE
        }
    }

    private fun PushTarget.toMessage(notification: PushNotification): Message =
        Message.builder()
            .setToken(fcmToken)
            .setNotification(
                Notification.builder()
                    .setTitle(notification.title)
                    .setBody(notification.body)
                    .build(),
            )
            // 목적지 식별자. 클라이언트가 추가 왕복 없이 라우트를 만든다(R11·AC11.4).
            .putAllData(notification.destination.asData())
            .build()

    /**
     * 이 응답으로 토큰을 지워야 하는가(R4).
     *
     * **`UNREGISTERED`만 무조건 지운다.** 앱이 등록 해제됐다는 뜻이고 삭제·재설치의 정상
     * 경로다. `INVALID_ARGUMENT`는 토큰 형식 문제일 때만 지운다 — 같은 코드가 우리 페이로드
     * 문제에서도 나오기 때문이다.
     *
     * 나머지(`UNAVAILABLE`·`INTERNAL`·`QUOTA_EXCEEDED`·`SENDER_ID_MISMATCH`·
     * `THIRD_PARTY_AUTH_ERROR`)로는 지우지 않는다. 장애나 설정 오류 한 번에 정상 토큰이
     * 전멸하기 때문이다.
     */
    private fun SendResponse.shouldDeleteToken(): Boolean {
        if (isSuccessful) return false
        return when (exception?.messagingErrorCode) {
            MessagingErrorCode.UNREGISTERED -> true
            MessagingErrorCode.INVALID_ARGUMENT -> isTokenFormatProblem(exception?.message)
            else -> false
        }
    }

    /**
     * `INVALID_ARGUMENT`가 **토큰** 문제인지 페이로드 문제인지 가린다(AC4.2).
     *
     * FCM은 잘못된 토큰과 잘못된 메시지에 같은 코드를 준다. 메시지 본문에서 토큰 필드를
     * 지목했는지로 가르며, **가릴 수 없으면 지우지 않는다** — 우리 페이로드 버그로 사용자
     * 토큰을 지우는 쪽이 더 나쁘다.
     */
    private fun isTokenFormatProblem(message: String?): Boolean {
        val text = message?.lowercase() ?: return false
        return TOKEN_PROBLEM_HINTS.any { it in text }
    }

    /** 발송 실패를 남긴다. **토큰 원문은 남기지 않는다**(POL-013) — 기기 식별자와 코드만. */
    private fun SendResponse.logIfNoteworthy(target: PushTarget) {
        if (isSuccessful) return
        val code = exception?.messagingErrorCode
        when (code) {
            // 설정 오류다. 삭제로 덮으면 원인이 사라지므로 오류로 남긴다(AC4.6).
            MessagingErrorCode.SENDER_ID_MISMATCH,
            MessagingErrorCode.THIRD_PARTY_AUTH_ERROR,
            -> log.error("FCM 설정 오류: code={} deviceId={}", code, target.deviceId)

            else -> log.warn("FCM 발송 실패: code={} deviceId={}", code, target.deviceId)
        }
    }

    private companion object {
        /**
         * `INVALID_ARGUMENT` 메시지에서 토큰 문제를 가리키는 표지.
         * FCM이 문구를 바꾸면 판정이 보수적으로 기울어 지우지 않게 된다 — 안전한 방향이다.
         */
        val TOKEN_PROBLEM_HINTS = listOf(
            "registration token",
            "not a valid fcm registration token",
            "invalid registration",
        )
    }
}
