package org.around.ieuri.community.infrastructure.adapter.out.event

import org.around.ieuri.community.application.port.out.event.ConversationEventPort
import org.around.ieuri.community.application.query.TypingEventView
import org.around.ieuri.community.infrastructure.adapter.out.event.mapper.NewMessagePayload
import org.around.ieuri.community.infrastructure.adapter.out.event.mapper.TypingEventPayload
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.scheduler.Scheduler
import tools.jackson.databind.ObjectMapper

/**
 * 대화 실시간 이벤트(타이핑·새 메시지)를 Valkey pub/sub으로 파드 간에 주고받는다(SPEC-0017, POL-048).
 * - 채널은 타이핑·메시지 둘로 나뉜다.
 * - 페이로드는 JSON이고 본문을 싣지 않는다(POL-013). 새 메시지는 id만 보내고 구독 측이 DB에서 읽는다.
 */
@Component
class ValkeyConversationEventAdapter(
    private val redis: StringRedisTemplate,
    private val listenerContainer: ReactiveRedisMessageListenerContainer,
    private val objectMapper: ObjectMapper,
    private val eventScheduler: Scheduler,
) : ConversationEventPort {

    /** 발행 실패를 삼킨다 — 유실 허용 이벤트다(AC14-2). */
    override fun publishTyping(event: TypingEventView) {
        publishQuietly(
            typingTopic(event.conversationId),
            objectMapper.writeValueAsString(TypingEventPayload.from(event)),
            event.conversationId,
        )
    }

    /** 저장된 뒤 발행한다. 발행 실패를 삼키며 전송을 실패시키지 않는다 — 수신자는 재조회로 본다. */
    override fun publishNewMessage(conversationId: Long, messageId: Long) {
        publishQuietly(
            messageTopic(conversationId),
            objectMapper.writeValueAsString(NewMessagePayload(messageId)),
            conversationId,
        )
    }

    /**
     * 구독 생명주기는 컨테이너에 맡긴다 — 취소 시 UNSUBSCRIBE까지 처리된다.
     * - 이벤트 루프에서 블로킹하면 모든 대화가 멎으므로 전용 스케줄러로 옮겨 방출한다.
     */
    override fun subscribeTyping(conversationId: Long): Flux<TypingEventView> = receive(typingTopic(conversationId)) { payload ->
        read(payload, TypingEventPayload::class.java)?.toView(conversationId)
    }

    override fun subscribeNewMessage(conversationId: Long): Flux<Long> = receive(messageTopic(conversationId)) { payload ->
        read(payload, NewMessagePayload::class.java)?.messageId
    }

    /** 채널 하나를 구독해 페이로드를 [parse]로 옮긴다. [parse]가 null이면 그 이벤트는 버린다. */
    private fun <T : Any> receive(channel: String, parse: (String) -> T?): Flux<T> = listenerContainer.receive(ChannelTopic(channel))
        .publishOn(eventScheduler)
        .mapNotNull<T> { parse(it.message) }

    /**
     * 깨진 페이로드는 경고 로그만 남기고 null을 돌려준다.
     * - 예외를 던지면 리스너 스레드가 죽어 그 파드의 구독이 통째로 멎는다.
     */
    private fun <T> read(payload: String, type: Class<T>): T? = try {
        objectMapper.readValue(payload, type)
    } catch (e: RuntimeException) {
        log.warn("대화 이벤트 페이로드를 해석할 수 없다: {}", e.message)
        null
    }

    private fun publishQuietly(topic: String, payload: String, conversationId: Long) {
        try {
            // 수신자 0은 발행 실패가 아니라 구독자 없음이다.
            val receivers = redis.convertAndSend(topic, payload)
            log.debug("대화 이벤트 발행: topic={} 수신자={}", topic, receivers)
        } catch (e: RuntimeException) {
            // 대화 id만 남긴다 — 본문도 수신자도 로그 대상이 아니다(POL-013).
            log.warn("failed to publish conversation event for conversation {}", conversationId, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ValkeyConversationEventAdapter::class.java)

        /** 발행과 구독은 반드시 같은 채널 이름을 써야 한다 — 이 함수로만 만든다. */
        fun typingTopic(conversationId: Long) = "ieuri:community:typing:$conversationId"

        fun messageTopic(conversationId: Long) = "ieuri:community:message:$conversationId"
    }
}
