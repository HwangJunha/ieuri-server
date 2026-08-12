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

    /**
     * 타이핑 표시. **유실 허용이 계약이다** — 버퍼가 차면 최신 이벤트만 남긴다. 밀린 타이핑을
     * 나중에 몰아 보내 봐야 의미가 없고, 쌓아 두면 메모리만 는다.
     */
    @DgsSubscription
    fun typingIndicator(@InputArgument conversationId: Long): Publisher<TypingEventView> =
        conversationQueryUseCase
            .typingIndicator(conversationId, currentMemberProvider.requireMemberId())
            .toFlux(FluxSink.OverflowStrategy.LATEST)

    /**
     * 새 메시지. **타이핑과 달리 버리지 않는다** — 버퍼가 차면 밀린 것을 쌓아 두고, 그래도
     * 넘치면 에러로 끊어 클라이언트가 재연결 후 조회로 메꾸게 한다. 조용히 버리면 대화에
     * 구멍이 뚫린 채로 남는다.
     */
    @DgsSubscription
    fun newMessage(@InputArgument conversationId: Long): Publisher<MessageView> =
        conversationQueryUseCase
            .newMessage(conversationId, currentMemberProvider.requireMemberId())
            .toFlux(FluxSink.OverflowStrategy.BUFFER)

    /**
     * 구독을 `Flux`로 옮긴다. **연결이 끊기면 브로커 구독을 반드시 뗀다** — 떼지 않으면
     * 리스너가 컨테이너에 쌓인다.
     */
    private fun <T : Any> ConversationSubscription<T>.toFlux(
        overflow: FluxSink.OverflowStrategy,
    ): Publisher<T> = Flux.create<T>({ sink: FluxSink<T> ->
        onEvent { sink.next(it) }
        sink.onDispose { cancel() }
    }, overflow)
}
