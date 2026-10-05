package org.around.ieuri.alarm.infrastructure.adapter.`in`.event

import org.around.ieuri.alarm.application.port.`in`.command.PushDispatchRequest
import org.around.ieuri.alarm.application.port.`in`.command.PushDispatchUseCase
import org.around.ieuri.alarm.domain.vo.PushDestination
import org.around.ieuri.alarm.infrastructure.config.AlarmAsyncConfig
import org.around.ieuri.community.domain.shared.MessageNotificationTriggered
import org.around.ieuri.community.domain.shared.SpotAnswerNotificationTriggered
import org.around.ieuri.community.domain.shared.SpotAnswerReplyNotificationTriggered
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * community의 알림 트리거를 구독해 발송으로 옮긴다(SPEC-0028 R5~R7).
 * - `AFTER_COMMIT`: 롤백된 작업의 알림은 나가지 않는다.
 * - `@Async`를 빼면 FCM 왕복이 요청 스레드의 응답 시간에 실린다(R12).
 * - 예외를 삼키고 재시도하지 않는다(AC12.3, POL-040).
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

    /** 트리거 종류와 식별자만 로그로 남긴다(POL-013). */
    private fun dispatchQuietly(request: PushDispatchRequest, trigger: String, sourceId: Long) {
        log.debug("푸시 트리거 수신: trigger={} sourceId={}", trigger, sourceId)
        try {
            pushDispatchUseCase.dispatch(request)
        } catch (e: Exception) {
            log.warn("push dispatch failed: trigger={} sourceId={}", trigger, sourceId, e)
        }
    }
}
