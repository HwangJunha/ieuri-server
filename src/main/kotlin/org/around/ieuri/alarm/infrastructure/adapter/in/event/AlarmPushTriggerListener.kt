package org.around.ieuri.alarm.infrastructure.adapter.`in`.event

import org.around.ieuri.alarm.application.port.`in`.command.PushDispatchRequest
import org.around.ieuri.alarm.application.port.`in`.command.PushDispatchUseCase
import org.around.ieuri.alarm.domain.vo.PushDestination
import org.around.ieuri.alarm.infrastructure.config.AlarmAsyncConfig
import org.around.ieuri.community.domain.event.MessageNotificationTriggered
import org.around.ieuri.community.domain.event.SpotAnswerNotificationTriggered
import org.around.ieuri.community.domain.event.SpotAnswerReplyNotificationTriggered
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.event.TransactionPhase

/**
 * community의 알림 트리거를 구독해 발송으로 옮긴다(SPEC-0028 §2, R5~R7).
 *
 * **community가 alarm을 부르지 않는다.** 부르면 community가 alarm에 의존하게 되고, 발송
 * 실패가 메시지 전송을 함께 실패시킬 수 있다(R12).
 *
 * `AFTER_COMMIT`인 이유는 **롤백된 메시지의 알림이 나가면 안 되기** 때문이다. 커밋 전에
 * 보내면 저장이 취소된 메시지의 푸시가 이미 도착해 있다.
 *
 * **`@Async`가 반드시 있어야 한다.** Spring 이벤트는 기본이 동기라 `AFTER_COMMIT` 콜백도
 * 커밋한 스레드 — 곧 사용자의 요청 스레드 — 에서 실행된다. 없으면 FCM으로 나가는 블로킹
 * HTTP 왕복이 메시지 전송 응답 시간에 그대로 실려, 발송이 느려질 때 전송도 함께 느려진다.
 * R12는 실패가 전파되지 않는 것만이 아니라 **지연도 전가되지 않는다**는 뜻이다.
 *
 * 예외를 삼키고 **재시도하지 않는다**(AC12.3) — 알림 1건 누락의 비용이 재시도 큐를 운영하는
 * 비용보다 작고, 재시도 간격에 댈 근거도 없다(POL-040).
 */
@Component
class AlarmPushTriggerListener(
    private val pushDispatchUseCase: PushDispatchUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(AlarmAsyncConfig.PUSH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMessage(event: MessageNotificationTriggered) {
        dispatchQuietly(
            PushDispatchRequest(
                recipientId = event.recipientId,
                actorId = event.senderId,
                destination = PushDestination.Dm(event.conversationId),
            ),
            "message",
            event.messageId,
        )
    }

    @Async(AlarmAsyncConfig.PUSH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSpotAnswer(event: SpotAnswerNotificationTriggered) {
        dispatchQuietly(
            PushDispatchRequest(
                recipientId = event.recipientId,
                actorId = event.actorId,
                destination = PushDestination.SpotQaAnswer(event.spotId, event.questionId),
            ),
            "spotAnswer",
            event.answerId,
        )
    }

    @Async(AlarmAsyncConfig.PUSH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSpotAnswerReply(event: SpotAnswerReplyNotificationTriggered) {
        dispatchQuietly(
            PushDispatchRequest(
                recipientId = event.recipientId,
                actorId = event.actorId,
                destination = PushDestination.SpotQaReply(event.spotId, event.questionId),
            ),
            "spotAnswerReply",
            event.replyId,
        )
    }

    /** 본문은 로그 대상이 아니다(POL-013) — 트리거 종류와 식별자만 남긴다. */
    private fun dispatchQuietly(request: PushDispatchRequest, trigger: String, sourceId: Long) {
        // 이벤트가 여기까지 왔다는 사실을 남긴다. 이 줄이 없으면 "알림이 안 온다"의 원인이
        // 트리거 미발행인지 발송 단계 탈락인지 가릴 수 없다.
        log.debug("푸시 트리거 수신: trigger={} sourceId={}", trigger, sourceId)
        try {
            pushDispatchUseCase.dispatch(request)
        } catch (e: Exception) {
            log.warn("push dispatch failed: trigger={} sourceId={}", trigger, sourceId, e)
        }
    }
}
