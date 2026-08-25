package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsSubscription
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.common.security.SubscriptionAuthorizer
import org.around.ieuri.community.application.port.`in`.query.ConversationQueryUseCase
import org.around.ieuri.community.application.query.MessageView
import org.around.ieuri.community.application.query.TypingEventView
import org.reactivestreams.Publisher
import org.springframework.stereotype.Component

/**
 * 대화 실시간 구독 리졸버(SPEC-0017 R9·R10·R14). **플랫폼 최초의 Subscription이다.**
 *
 * **참여자 검사는 유스케이스가 subscribe 시점에 한다**(POL-048) — 연결이 살아 있다는 것이
 * 특정 대화를 볼 자격의 증명은 아니다. 참여자가 아니면 ForbiddenException이 여기서 잡히지
 * 않고 최상위 errors[]로 전파된다(AC10-3).
 *
 * 스트림은 어댑터가 **채널당 하나씩** 들고 있는 것을 그대로 받는다 — 구독마다 만들면 같은
 * 채널에 리스너가 여러 개 붙어 이벤트가 유실된다.
 */
@DgsComponent
@Component
class ConversationSubscriptionResolver(
    private val conversationQueryUseCase: ConversationQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
    private val subscriptionAuthorizer: SubscriptionAuthorizer,
) {

    /** 타이핑 표시. 유실 허용이 계약이다(AC14-2). */
    @DgsSubscription
    fun typingIndicator(
        @InputArgument conversationId: Long,
        dfe: DgsDataFetchingEnvironment,
    ): Publisher<TypingEventView> =
        conversationQueryUseCase.typingIndicator(conversationId, liveMemberId(dfe))

    /**
     * 새 메시지. **타이핑과 달리 유실되면 안 된다** — 끊긴 동안의 메시지는 클라이언트가
     * 재연결 후 conversationMessages로 메꾼다(계약에 명시).
     */
    @DgsSubscription
    fun newMessage(
        @InputArgument conversationId: Long,
        dfe: DgsDataFetchingEnvironment,
    ): Publisher<MessageView> =
        conversationQueryUseCase.newMessage(conversationId, liveMemberId(dfe))

    /**
     * 구독을 열 자격이 있는 회원 id(SPEC-0032 R7).
     *
     * **소켓 신원이 살아 있는지를 여기서 본다** — `connection_init`의 신원은 세션 속성에
     * 담겨 만료 개념이 없어서, 이 검사가 없으면 로그아웃·정지·탈퇴 이후에도 연결 수명 내내
     * 그 회원으로 스트리밍된다. 참여자 검사는 그대로 유스케이스가 맡는다.
     */
    private fun liveMemberId(dfe: DgsDataFetchingEnvironment): Long {
        val memberId = currentMemberProvider.requireMemberId()
        subscriptionAuthorizer.requireLiveIdentity(dfe, memberId)
        return memberId
    }
}
