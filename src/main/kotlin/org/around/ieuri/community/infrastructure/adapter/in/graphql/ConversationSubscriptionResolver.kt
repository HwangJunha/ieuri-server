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
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

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

    /**
     * 타이핑 표시. **유실 허용이 계약이다** — 밀리면 최신 것만 남긴다. 지난 타이핑을 나중에
     * 몰아 보내 봐야 의미가 없고, 쌓아 두면 메모리만 는다.
     */
    @DgsSubscription
    fun typingIndicator(@InputArgument conversationId: Long): Publisher<TypingEventView> =
        conversationQueryUseCase
            .typingIndicator(conversationId, currentMemberProvider.requireMemberId())
            .toFlux()
            .onBackpressureLatest()
            .publishOn(EVENT_SCHEDULER, 1)

    /**
     * 새 메시지. **타이핑과 달리 버리지 않는다** — 밀린 것을 버퍼에 쌓는다.
     *
     * 상한을 넘기면 에러로 끊어 클라이언트가 재연결 후 조회로 메꾸게 한다(계약에 명시). 조용히
     * 버리면 대화에 구멍이 뚫린 채로 남아 사용자가 알아챌 방법이 없다. 한 대화의 사람이 둘뿐이라
     * 이 상한에 닿는 것은 정상 상황이 아니다.
     */
    @DgsSubscription
    fun newMessage(@InputArgument conversationId: Long): Publisher<MessageView> =
        conversationQueryUseCase
            .newMessage(conversationId, currentMemberProvider.requireMemberId())
            .toFlux()
            .onBackpressureBuffer(MESSAGE_BUFFER_SIZE)
            .publishOn(EVENT_SCHEDULER, 1)

    /**
     * 구독을 `Flux`로 옮긴다. **연결이 끊기면 브로커 구독을 반드시 뗀다** — 떼지 않으면
     * 리스너가 컨테이너에 쌓인다.
     *
     * **오버플로 전략을 여기서 정하지 않는다.** `Flux.create`에 BUFFER를 주면 구독자가 요청한
     * 만큼만 나가고 나머지는 버퍼에 갇히는데, 전송 계층이 하나씩 당겨가는 구조에서는 첫 건이
     * 나간 뒤 멈춘 것처럼 보인다(실측: 발행·수신 로그는 남는데 이후 조회가 없었다).
     * 백프레셔는 호출부가 `onBackpressure*` 연산자로 명시한다.
     */
    private fun <T : Any> ConversationSubscription<T>.toFlux(): Flux<T> =
        Flux.create({ sink: FluxSink<T> ->
            onEvent { sink.next(it) }
            sink.onDispose { cancel() }
        }, FluxSink.OverflowStrategy.IGNORE)

    private companion object {
        /**
         * 새 메시지 버퍼 상한. 1:1 대화라 정상 상황에서는 닿지 않는다 — 닿았다면 클라이언트가
         * 소비하지 못하는 상태이므로 끊고 재연결시키는 편이 낫다.
         */
        const val MESSAGE_BUFFER_SIZE = 256

        /**
         * 이벤트 방출 스레드. **브로커 리스너 스레드에서 그대로 흘리면 안 된다.**
         *
         * MVC 전송 계층(GraphQlWebSocketHandler.SendMessageSubscriber)은 `hookOnNext`에서
         * `WebSocketSession.sendMessage`를 **블로킹으로** 호출하고, 성공했을 때만 다음 하나를
         * 요청한다(`request(1)`). 그런데 `sendMessage`는 스레드 안전하지 않아 호출이 겹치면
         * IOException이 나고, 그 경로는 요청을 잇지 않고 `cancel()`로 구독을 끊는다.
         *
         * Valkey 이벤트는 매번 다른 리스너 스레드(enerContainer-N)에서 올라오므로 이 조건에
         * 그대로 걸린다 — 첫 건만 나가고 이후가 멎는 증상이 정확히 그것이었다.
         *
         * 단일 스레드로 옮겨 전송을 직렬화한다. 전송은 짧고 구독 수가 많아도 이벤트 자체가
         * 드물어 한 스레드로 충분하다.
         *
         * **prefetch를 1로 둔다.** 기본값(256)이면 publishOn이 상위에 256개를 미리 요청해
         * 자기 큐에 쌓아 두는데, 하위 전송은 `request(1)`씩만 당겨가므로 큐에 갇힌 이벤트가
         * 나가지 못한다(실측: 5건 발행에 2건만 전달). 1로 맞추면 요청이 그대로 이어진다.
         */
        private val EVENT_SCHEDULER: Scheduler =
            Schedulers.newSingle("community-subscription", true)
    }
}
