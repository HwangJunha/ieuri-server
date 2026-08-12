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
import reactor.core.scheduler.Schedulers
import tools.jackson.databind.ObjectMapper

/**
 * 대화 실시간 이벤트를 Valkey pub/sub으로 주고받는다(SPEC-0017 R9·R10·R14, POL-048).
 *
 * **공유 브로커를 거치는 것이 핵심이다.** 서비스는 k3s에서 여러 파드로 운영되므로 in-process
 * 이벤트로는 발신자와 수신자가 다른 파드에 있을 때 전달되지 않는다. 그 실패는 조용해서 단일
 * 인스턴스 개발 환경에서는 완벽히 동작하고 운영에서만 "가끔 되는 기능"이 된다.
 *
 * Valkey는 이미 토큰 저장소로 스택에 있어 새 인프라를 들이지 않는다. **Kafka는 쓰지 않는다** —
 * 두 이벤트 모두 브로커에 영속 로그를 요구하지 않는다. 타이핑은 유실 허용이고, 메시지는
 * **DB가 원본이라** 브로커가 보관할 이유가 없다.
 *
 * **채널을 둘로 나눈다.** 한 채널에 섞으면 타이핑만 필요한 구독자도 메시지 이벤트를 받아
 * 걸러내야 하고, 페이로드에 종류 판별자가 붙는다.
 *
 * **페이로드는 JSON이고 본문이 실리지 않는다**(POL-013). 새 메시지는 id만 보내고 구독
 * 시점에 DB에서 읽으므로 값의 원본이 DB 하나로 유지된다.
 */
@Component
class ValkeyConversationEventAdapter(
    private val redis: StringRedisTemplate,
    private val listenerContainer: ReactiveRedisMessageListenerContainer,
    private val objectMapper: ObjectMapper,
) : ConversationEventPort {

    /**
     * **발행 실패를 삼킨다.** 타이핑 표시를 살리자고 다른 기능까지 실패시키지 않는다 —
     * 유실 허용 이벤트이므로 이것이 정상 동작이다(AC14-2).
     */
    override fun publishTyping(event: TypingEventView) {
        publishQuietly(
            typingTopic(event.conversationId),
            objectMapper.writeValueAsString(TypingEventPayload.from(event)),
            event.conversationId,
        )
    }

    /**
     * **메시지는 이미 저장된 뒤 발행된다.** 발행이 실패해도 메시지는 DB에 남아 있으므로
     * 전송 자체를 실패시키지 않는다 — 수신자는 재조회로 그 메시지를 보게 된다.
     */
    override fun publishNewMessage(conversationId: Long, messageId: Long) {
        publishQuietly(
            messageTopic(conversationId),
            objectMapper.writeValueAsString(NewMessagePayload(messageId)),
            conversationId,
        )
    }

    /**
     * **구독 생명주기를 컨테이너에 맡긴다.** 리스너를 직접 붙이면 뗄 책임이 이쪽으로 넘어오고,
     * 그것을 빠뜨리면 파드가 사는 동안 브로커 구독이 단조 증가한다. `receive()`의 Flux는
     * 구독이 취소될 때 UNSUBSCRIBE까지 처리한다.
     *
     * 스트림은 컨테이너의 이벤트 루프에서 방출되므로 **블로킹해도 안전한 스레드로 옮겨서**
     * 넘긴다 — 구독자가 DB를 읽어도 다른 대화의 전달이 멈추지 않는다. 어느 스레드에서
     * 올라오는지는 이 어댑터만 아는 사실이라 결정도 여기서 한다.
     */
    override fun subscribeTyping(conversationId: Long): Flux<TypingEventView> =
        receive(typingTopic(conversationId)) { payload ->
            read(payload, TypingEventPayload::class.java)?.toView(conversationId)
        }

    override fun subscribeNewMessage(conversationId: Long): Flux<Long> =
        receive(messageTopic(conversationId)) { payload ->
            read(payload, NewMessagePayload::class.java)?.messageId
        }

    /**
     * 채널 하나를 구독해 페이로드를 [parse]로 옮긴다. [parse]가 null이면 그 이벤트는 조용히
     * 버린다 — 형식이 어긋난 페이로드를 구독자에게 전달할 방법이 없다.
     */
    private fun <T : Any> receive(channel: String, parse: (String) -> T?): Flux<T> =
        listenerContainer.receive(ChannelTopic(channel))
            .doOnSubscribe { log.info("[진단] 채널 구독 시작: {}", channel) }
            .doOnNext { log.info("[진단] 브로커 수신: topic={} payload={}", channel, it.message) }
            .doOnCancel { log.info("[진단] 채널 구독 취소: {}", channel) }
            .doOnError { log.warn("[진단] 채널 스트림 오류: {}", channel, it) }
            .doOnComplete { log.info("[진단] 채널 스트림 종료: {}", channel) }
            .publishOn(Schedulers.boundedElastic())
            .mapNotNull<T> { parse(it.message) }
            .doOnNext { log.info("[진단] 변환 완료 → 유스케이스로: topic={}", channel) }

    /**
     * 깨진 페이로드는 **조용히 버린다**(null 반환) — 구독자에게 전달할 방법이 없는데 예외를
     * 던지면 리스너 스레드가 죽어 그 파드의 구독이 통째로 멎는다. 형식 오류는 배포 스큐나
     * 외부 발행자를 뜻하므로 건수만 로그로 남긴다.
     *
     * 대상 타입을 인자로 받는다 — 호출부에서 `::class.java`를 반복하지만 그 편이 명시적이다.
     */
    private fun <T> read(payload: String, type: Class<T>): T? =
        try {
            objectMapper.readValue(payload, type)
        } catch (e: RuntimeException) {
            log.warn("대화 이벤트 페이로드를 해석할 수 없다: {}", e.message)
            null
        }

    private fun publishQuietly(topic: String, payload: String, conversationId: Long) {
        try {
            // **받은 구독자 수를 함께 남긴다.** 0이면 발행은 됐으나 아무도 안 듣는 것이라
            // 발행 실패와 구분된다 — 이 둘을 못 나누면 원인을 좁힐 수 없다.
            val receivers = redis.convertAndSend(topic, payload)
            log.info("[진단] 발행: topic={} 수신자={}", topic, receivers)
        } catch (e: RuntimeException) {
            // 대화 id만 남긴다 — 본문도 수신자도 로그 대상이 아니다(POL-013).
            log.warn("failed to publish conversation event for conversation {}", conversationId, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ValkeyConversationEventAdapter::class.java)

        /**
         * 발행과 구독이 **반드시 같은 채널 이름을 써야 한다** — 한쪽만 바뀌면 조용히 아무것도
         * 전달되지 않으므로 한 곳에서만 만든다.
         */
        fun typingTopic(conversationId: Long) = "ieuri:community:typing:$conversationId"

        fun messageTopic(conversationId: Long) = "ieuri:community:message:$conversationId"
    }
}

