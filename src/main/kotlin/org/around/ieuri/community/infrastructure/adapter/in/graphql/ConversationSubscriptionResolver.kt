package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsSubscription
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.application.port.`in`.query.ConversationQueryUseCase
import org.around.ieuri.community.application.port.`in`.query.ConversationSubscription
import org.around.ieuri.community.application.query.MessageView
import org.around.ieuri.community.application.query.TypingEventView
import org.reactivestreams.Publisher
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.ArrayBlockingQueue

/**
 * 대화 실시간 구독 리졸버(SPEC-0017 R9·R10·R14). **플랫폼 최초의 Subscription이다.**
 *
 * **참여자 검사는 유스케이스가 subscribe 시점에 한다**(POL-048) — 연결이 살아 있다는 것이
 * 특정 대화를 볼 자격의 증명은 아니다. 참여자가 아니면 ForbiddenException이 여기서 잡히지
 * 않고 최상위 errors[]로 전파된다(AC10-3).
 *
 * `Publisher`로 옮기는 일이 **이 계층의 몫이다** — application은 리액티브 라이브러리를 알지
 * 못하며 [ConversationSubscription] 콜백만 노출한다.
 *
 * **두 구독의 백프레셔 전략이 다르다.** 타이핑은 밀리면 최신 것만 남기면 되지만(과거 타이핑은
 * 의미가 없다), 메시지는 버리면 대화가 비므로 버퍼에 쌓는다.
 */
@DgsComponent
@Component
class ConversationSubscriptionResolver(
    private val conversationQueryUseCase: ConversationQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    /** 타이핑 표시. 유실 허용이 계약이다(AC14-2). */
    @DgsSubscription
    fun typingIndicator(@InputArgument conversationId: Long): Publisher<TypingEventView> =
        conversationQueryUseCase
            .typingIndicator(conversationId, currentMemberProvider.requireMemberId())
            .toFlux()

    /**
     * 새 메시지. **타이핑과 달리 유실되면 안 된다** — 끊긴 동안의 메시지는 클라이언트가
     * 재연결 후 conversationMessages로 메꾼다(계약에 명시).
     */
    @DgsSubscription
    fun newMessage(@InputArgument conversationId: Long): Publisher<MessageView> =
        conversationQueryUseCase
            .newMessage(conversationId, currentMemberProvider.requireMemberId())
            .toFlux()

    /**
     * 구독을 `Flux`로 옮긴다. **연결이 끊기면 브로커 구독을 반드시 뗀다** — 떼지 않으면
     * 리스너가 컨테이너에 쌓인다.
     *
     * **`Flux.create` 대신 `Sinks.many()`를 쓴다.** 브로커 이벤트는 매번 다른 리스너
     * 스레드(enerContainer-N)에서 올라오는데, `FluxSink.next()`는 여러 스레드에서 동시에
     * 부르는 것을 전제하지 않아 이벤트가 **불규칙하게 유실된다**(실측: 8건 중 1·3·6·7번만
     * 도착). 한 개만 오는 것도, 전부 오는 것도 아닌 이 무작위성이 경쟁 상태의 신호였다.
     *
     * `Sinks.many().unicast()`의 `tryEmitNext`는 멀티스레드 발행을 명시적으로 다루며,
     * 실패를 반환값으로 알려 준다 — 조용히 사라지지 않는다.
     *
     * 버퍼는 무제한이 아니라 상한을 둔 큐다. 전송 계층이 `request(1)`씩만 당겨가므로 요청이
     * 없는 사이의 이벤트를 쌓아 둬야 하지만, 무한정 쌓으면 소비하지 못하는 구독이 메모리를
     * 잠식한다.
     */
    private fun <T : Any> ConversationSubscription<T>.toFlux(): Flux<T> {
        val sink = Sinks.many().unicast().onBackpressureBuffer<T>(
            ArrayBlockingQueue(EVENT_BUFFER_SIZE),
        )
        onEvent { event ->
            // 실패를 삼키지 않는다 — 버퍼 초과·구독 종료가 조용히 유실로 보이면 안 된다.
            val result = sink.tryEmitNext(event)
            if (result.isFailure) log.warn("구독 이벤트를 방출하지 못했다: {}", result)
        }
        return sink.asFlux()
            // **요청 흐름을 직접 관측한다.** 전송 계층이 첫 건 뒤에 request(1)을 다시 보내는지가
            // 남은 미지수다 — 보낸다면 우리 쪽 문제이고, 안 보낸다면 전송 계층에서 멎은 것이다.
            .doOnRequest { log.info("구독 수요 요청: {}건", it) }
            .doOnNext { log.info("구독 이벤트 전달 시작") }
            .doOnCancel { log.info("구독 취소됨") }
            .doOnError { log.warn("구독 스트림 오류", it) }
            .doFinally { log.info("구독 종료: {}", it); cancel() }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ConversationSubscriptionResolver::class.java)

        /** 요청이 없는 사이의 이벤트를 쌓아 둘 상한. 1:1 대화라 정상 상황에서는 닿지 않는다. */
        const val EVENT_BUFFER_SIZE = 256

    }
}
