package org.around.ieuri.community.adapter.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsSubscription
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.common.security.SubscriptionAuthorizer
import org.around.ieuri.community.application.conversation.provided.ConversationQueryUseCase
import org.around.ieuri.community.application.conversation.provided.dto.MessageView
import org.around.ieuri.community.application.conversation.provided.dto.TypingEventView
import org.reactivestreams.Publisher
import org.springframework.stereotype.Component

/**
 * 대화 실시간 구독 리졸버(SPEC-0017).
 * - 참여자 검사는 유스케이스가 subscribe 시점에 한다(POL-048). ForbiddenException은 최상위 errors[]로 전파된다(AC10-3).
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
    ): Publisher<TypingEventView> = conversationQueryUseCase.typingIndicator(conversationId, liveMemberId(dfe))

    /** 새 메시지. 끊긴 동안의 메시지는 클라이언트가 재연결 후 conversationMessages로 메꾼다. */
    @DgsSubscription
    fun newMessage(
        @InputArgument conversationId: Long,
        dfe: DgsDataFetchingEnvironment,
    ): Publisher<MessageView> = conversationQueryUseCase.newMessage(conversationId, liveMemberId(dfe))

    /**
     * 구독을 열 자격이 있는 회원 id(SPEC-0032 R7).
     * - 소켓 신원이 아직 유효한지 확인한다 — 빼면 로그아웃·정지·탈퇴 뒤에도 스트리밍이 이어진다.
     */
    private fun liveMemberId(dfe: DgsDataFetchingEnvironment): Long {
        val memberId = currentMemberProvider.requireMemberId()
        subscriptionAuthorizer.requireLiveIdentity(dfe, memberId)
        return memberId
    }
}
