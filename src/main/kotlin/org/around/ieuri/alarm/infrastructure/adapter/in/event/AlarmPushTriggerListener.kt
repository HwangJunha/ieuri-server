package org.around.ieuri.alarm.infrastructure.adapter.`in`.event

import org.around.ieuri.alarm.application.port.`in`.command.PushDispatchRequest
import org.around.ieuri.alarm.application.port.`in`.command.PushDispatchUseCase
import org.around.ieuri.alarm.domain.vo.PushDestination
import org.around.ieuri.community.domain.event.MessageNotificationTriggered
import org.around.ieuri.community.domain.event.SpotAnswerNotificationTriggered
import org.around.ieuri.community.domain.event.SpotAnswerReplyNotificationTriggered
import org.slf4j.LoggerFactory
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
 * 예외를 삼키고 **재시도하지 않는다**(AC12.3) — 알림 1건 누락의 비용이 재시도 큐를 운영하는
 * 비용보다 작고, 재시도 간격에 댈 근거도 없다(POL-040).
 */
@Component
class AlarmPushTriggerListener(
    private val pushDispatchUseCase: PushDispatchUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMessage(event: MessageNotificationTriggered) {
        dispatchQuietly(
            PushDispatchRequest(
                recipientId = event.recipientId,
                actorId = event.senderId,
                destination = PushDestination.Dm(event.conversationId),
                body = event.body,
            ),
            "message",
            event.messageId,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSpotAnswer(event: SpotAnswerNotificationTriggered) {
        dispatchQuietly(
            PushDispatchRequest(
                recipientId = event.recipientId,
                actorId = event.actorId,
                destination = PushDestination.SpotQaAnswer(event.spotId, event.questionId),
                body = event.body,
            ),
            "spotAnswer",
            event.answerId,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSpotAnswerReply(event: SpotAnswerReplyNotificationTriggered) {
        dispatchQuietly(
            PushDispatchRequest(
                recipientId = event.recipientId,
                actorId = event.actorId,
                destination = PushDestination.SpotQaReply(event.spotId, event.questionId),
                body = event.body,
            ),
            "spotAnswerReply",
            event.replyId,
        )
    }

    /** 본문도 수신자도 로그 대상이 아니다(POL-013) — 트리거 종류와 식별자만 남긴다. */
    private fun dispatchQuietly(request: PushDispatchRequest, trigger: String, sourceId: Long) {
        try {
            pushDispatchUseCase.dispatch(request)
        } catch (e: Exception) {
            log.warn("push dispatch failed: trigger={} sourceId={}", trigger, sourceId, e)
        }
    }
}
