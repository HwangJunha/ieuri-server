package org.around.ieuri.alarm.adapter.client

import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.google.firebase.messaging.SendResponse
import org.around.ieuri.alarm.application.push.required.PushSenderPort
import org.around.ieuri.alarm.application.push.required.dto.PushSendResult
import org.around.ieuri.alarm.application.push.required.dto.PushTarget
import org.around.ieuri.alarm.domain.push.PushNotification
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * FCM HTTP v1 발송 어댑터(SPEC-0028).
 * - FCM 오류 코드를 해석해 애플리케이션에는 지울 기기 목록만 돌려준다(AC4.3).
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
            // sendEach는 입력 순서대로 결과를 돌려준다 — 인덱스로 기기를 되짚는다.
            val invalid = response.responses.withIndex()
                .filter { (_, each) -> each.shouldDeleteToken() }
                .map { (index, _) -> targets[index].deviceId }

            response.responses.forEachIndexed { index, each -> each.logIfNoteworthy(targets[index]) }

            // 성공은 FCM이 접수했다는 뜻이지 기기에 도착했다는 뜻이 아니다.
            log.info(
                "FCM 접수 완료: 성공={} 실패={} 무효토큰={}",
                response.successCount,
                response.failureCount,
                invalid.size,
            )
            PushSendResult(successCount = response.successCount, invalidDeviceIds = invalid)
        } catch (e: Exception) {
            // 전체 실패: 재시도하지 않고 토큰도 지우지 않는다(R12·AC12.3).
            // 스택트레이스 없이 메시지만 남긴다(POL-013).
            log.warn("FCM 발송에 실패했다: targets={} cause={}", targets.size, e.message)
            PushSendResult.NONE
        }
    }

    private fun PushTarget.toMessage(notification: PushNotification): Message = Message.builder()
        .setToken(fcmToken)
        // 본문은 서버가 정한 안내 문구다(R13). 본문을 빼면 iOS가 알림을 조용히 버린다.
        .setNotification(
            Notification.builder()
                .setTitle(notification.title)
                .setBody(notification.body)
                .build(),
        )
        // 목적지 식별자(R11·AC11.4).
        .putAllData(notification.destination.asData())
        .setApnsConfig(iosConfig())
        .build()

    /**
     * iOS 전용 설정. Android에는 영향이 없다.
     * - `content-available`이 없으면 포그라운드 앱이 알림을 전달받지 못한다(R15).
     * - `content-available`과 함께면 `apns-priority`는 5여야 한다. 10이면 APNs가 거부한다.
     * - `alert`를 여기서 채우지 않는다. 채우면 `Notification`의 제목·본문을 덮어쓴다.
     */
    private fun iosConfig(): ApnsConfig = ApnsConfig.builder()
        .putHeader("apns-priority", APNS_PRIORITY_BACKGROUND_ELIGIBLE)
        .setAps(Aps.builder().setContentAvailable(true).build())
        .build()

    /**
     * 이 응답으로 토큰을 지워야 하는가(R4).
     * - `UNREGISTERED`는 지우고, `INVALID_ARGUMENT`는 토큰 형식 문제일 때만 지운다.
     * - 그 밖의 코드로는 지우지 않는다.
     */
    private fun SendResponse.shouldDeleteToken(): Boolean {
        if (isSuccessful) return false
        return when (exception?.messagingErrorCode) {
            MessagingErrorCode.UNREGISTERED -> true
            MessagingErrorCode.INVALID_ARGUMENT -> isTokenFormatProblem(exception?.message)
            else -> false
        }
    }

    /** `INVALID_ARGUMENT`가 토큰 문제인지 오류 메시지로 가린다(AC4.2). 가릴 수 없으면 false. */
    private fun isTokenFormatProblem(message: String?): Boolean {
        val text = message?.lowercase() ?: return false
        return TOKEN_PROBLEM_HINTS.any { it in text }
    }

    /** 발송 실패를 남긴다. 토큰 원문은 남기지 않는다(POL-013). */
    private fun SendResponse.logIfNoteworthy(target: PushTarget) {
        if (isSuccessful) return
        val code = exception?.messagingErrorCode
        when (code) {
            // 설정 오류는 error로 남긴다(AC4.6).
            MessagingErrorCode.SENDER_ID_MISMATCH,
            MessagingErrorCode.THIRD_PARTY_AUTH_ERROR,
            -> log.error("FCM 설정 오류: code={} deviceId={}", code, target.deviceId)

            else -> log.warn("FCM 발송 실패: code={} deviceId={}", code, target.deviceId)
        }
    }

    private companion object {
        /** APNs 우선순위. `content-available`과 함께 쓰려면 10이 아니라 5여야 한다. */
        const val APNS_PRIORITY_BACKGROUND_ELIGIBLE = "5"

        /** `INVALID_ARGUMENT` 메시지에서 토큰 문제를 가리키는 표지. 문구가 바뀌면 지우지 않는 쪽으로 기운다. */
        val TOKEN_PROBLEM_HINTS = listOf(
            "registration token",
            "not a valid fcm registration token",
            "invalid registration",
        )
    }
}
