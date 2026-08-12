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
     * **BUFFER 전략이다.** 전송 계층(SendMessageSubscriber)은 `request(1)`씩만 당겨가므로,
     * 요청이 없는 사이에 온 이벤트를 버려서는 안 된다 — 버리면 대화에 구멍이 뚫린다.
     *
     * 연산자를 더 얹지 않는다. publishOn·onBackpressure*를 끼웠더니 전달 개수만 달라지고
     * 문제가 옮겨 다녔다 — 계층을 늘리는 대신 가장 단순한 형태에서 다시 관측한다.
     */
    private fun <T : Any> ConversationSubscription<T>.toFlux(): Flux<T> =
        Flux.create({ sink: FluxSink<T> ->
            onEvent { sink.next(it) }
            sink.onDispose { cancel() }
        }, FluxSink.OverflowStrategy.BUFFER)

    private companion object {

    }
}
