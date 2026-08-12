package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.generated.types.CreateFeedItemInput
import org.around.ieuri.common.graphql.generated.types.CreateFeedItemPayload
import org.around.ieuri.common.graphql.generated.types.LikeFeedItemInput
import org.around.ieuri.common.graphql.generated.types.LikeFeedItemPayload
import org.around.ieuri.common.graphql.generated.types.NotifyTypingInput
import org.around.ieuri.common.graphql.generated.types.NotifyTypingPayload
import org.around.ieuri.common.graphql.generated.types.ReportMessagesInput
import org.around.ieuri.common.graphql.generated.types.ReportMessagesPayload
import org.around.ieuri.common.graphql.generated.types.SendMessageInput
import org.around.ieuri.common.graphql.generated.types.SendMessagePayload
import org.around.ieuri.common.graphql.generated.types.StartConversationInput
import org.around.ieuri.common.graphql.generated.types.StartConversationPayload
import org.around.ieuri.common.graphql.generated.types.UnlikeFeedItemInput
import org.around.ieuri.common.graphql.generated.types.UnlikeFeedItemPayload
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.application.command.LikeFeedItemCommand
import org.around.ieuri.community.application.command.NotifyTypingCommand
import org.around.ieuri.community.application.command.StartConversationCommand
import org.around.ieuri.community.application.port.`in`.command.ConversationCommandUseCase
import org.around.ieuri.community.application.port.`in`.command.FeedItemCommandUseCase
import org.around.ieuri.community.application.port.`in`.command.FeedItemLikeCommandUseCase
import org.around.ieuri.community.application.port.`in`.command.MessageReportCommandUseCase
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toCommand
import org.springframework.stereotype.Component

/**
 * 피드·DM 쓰기 리졸버(SPEC-0017). 전부 인증이 필요하며, 비로그인은 UnauthorizedException이
 * withCommunityUserErrors에 붙잡히지 않고 최상위 errors[]로 전파된다.
 *
 * 리졸버는 얇게 둔다 — 입력을 커맨드로 옮기고 결과를 payload로 옮길 뿐 판단하지 않는다.
 * 입력→커맨드 변환은 필드가 많은 것만 확장 함수로 뺐다(POL-033) — 나머지는 인자 하나라
 * 뒤바뀔 여지가 없다.
 */
@DgsComponent
@Component
class FeedMutationResolver(
    private val feedItemCommandUseCase: FeedItemCommandUseCase,
    private val feedItemLikeCommandUseCase: FeedItemLikeCommandUseCase,
    private val conversationCommandUseCase: ConversationCommandUseCase,
    private val messageReportCommandUseCase: MessageReportCommandUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    @DgsMutation
    fun createFeedItem(@InputArgument input: CreateFeedItemInput): CreateFeedItemPayload =
        withCommunityUserErrors(
            onError = { CreateFeedItemPayload(success = false, userErrors = it, feedItemId = null) },
        ) {
            val memberId = currentMemberProvider.requireMemberId()
            val feedItemId = feedItemCommandUseCase.createFeedItem(input.toCommand(), memberId)
            // feedItem을 채우지 않고 id만 주는 이유는 createPost와 같다 — 생성 타입이
            // spot·author를 생성자에서 요구하는데 그 둘은 지연 필드다.
            CreateFeedItemPayload(success = true, userErrors = emptyList(), feedItemId = feedItemId)
        }

    @DgsMutation
    fun likeFeedItem(@InputArgument input: LikeFeedItemInput): LikeFeedItemPayload =
        withCommunityUserErrors(
            onError = { LikeFeedItemPayload(success = false, userErrors = it, likeCount = null) },
        ) {
            val memberId = currentMemberProvider.requireMemberId()
            val likeCount = feedItemLikeCommandUseCase.like(
                LikeFeedItemCommand(input.feedItemId),
                memberId,
            )
            LikeFeedItemPayload(success = true, userErrors = emptyList(), likeCount = likeCount)
        }

    @DgsMutation
    fun unlikeFeedItem(@InputArgument input: UnlikeFeedItemInput): UnlikeFeedItemPayload =
        withCommunityUserErrors(
            onError = { UnlikeFeedItemPayload(success = false, userErrors = it, likeCount = null) },
        ) {
            val memberId = currentMemberProvider.requireMemberId()
            val likeCount = feedItemLikeCommandUseCase.unlike(
                LikeFeedItemCommand(input.feedItemId),
                memberId,
            )
            UnlikeFeedItemPayload(success = true, userErrors = emptyList(), likeCount = likeCount)
        }

    @DgsMutation
    fun startConversation(
        @InputArgument input: StartConversationInput,
    ): StartConversationPayload = withCommunityUserErrors(
        onError = {
            StartConversationPayload(success = false, userErrors = it, conversationId = null)
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val conversationId = conversationCommandUseCase.startConversation(
            StartConversationCommand(input.feedItemId),
            memberId,
        )
        StartConversationPayload(
            success = true,
            userErrors = emptyList(),
            conversationId = conversationId,
        )
    }

    @DgsMutation
    fun sendMessage(@InputArgument input: SendMessageInput): SendMessagePayload =
        withCommunityUserErrors(
            onError = { SendMessagePayload(success = false, userErrors = it, messageId = null) },
        ) {
            val memberId = currentMemberProvider.requireMemberId()
            val messageId = conversationCommandUseCase.sendMessage(input.toCommand(), memberId)
            SendMessagePayload(success = true, userErrors = emptyList(), messageId = messageId)
        }

    /** 수신자가 없어도 성공한다(AC10-2) — 받을 사람이 없는 것은 실패가 아니다. */
    @DgsMutation
    fun notifyTyping(@InputArgument input: NotifyTypingInput): NotifyTypingPayload =
        withCommunityUserErrors(
            onError = { NotifyTypingPayload(success = false, userErrors = it) },
        ) {
            val memberId = currentMemberProvider.requireMemberId()
            conversationCommandUseCase.notifyTyping(
                NotifyTypingCommand(input.conversationId),
                memberId,
            )
            NotifyTypingPayload(success = true, userErrors = emptyList())
        }

    @DgsMutation
    fun reportMessages(@InputArgument input: ReportMessagesInput): ReportMessagesPayload =
        withCommunityUserErrors(
            onError = { ReportMessagesPayload(success = false, userErrors = it) },
        ) {
            val memberId = currentMemberProvider.requireMemberId()
            messageReportCommandUseCase.reportMessages(input.toCommand(), memberId)
            ReportMessagesPayload(success = true, userErrors = emptyList())
        }
}
