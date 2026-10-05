package org.around.ieuri.community.adapter.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.generated.types.MarkConversationReadInput
import org.around.ieuri.common.graphql.generated.types.MarkConversationReadPayload
import org.around.ieuri.common.graphql.generated.types.NotifyTypingInput
import org.around.ieuri.common.graphql.generated.types.NotifyTypingPayload
import org.around.ieuri.common.graphql.generated.types.ReportMessagesInput
import org.around.ieuri.common.graphql.generated.types.ReportMessagesPayload
import org.around.ieuri.common.graphql.generated.types.SendMessageInput
import org.around.ieuri.common.graphql.generated.types.SendMessagePayload
import org.around.ieuri.common.graphql.generated.types.StartConversationInput
import org.around.ieuri.common.graphql.generated.types.StartConversationPayload
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.adapter.graphql.contentLanguage
import org.around.ieuri.community.adapter.graphql.mapper.toCommand
import org.around.ieuri.community.adapter.graphql.withCommunityUserErrors
import org.around.ieuri.community.application.conversation.provided.ConversationCommandUseCase
import org.around.ieuri.community.application.conversation.provided.dto.NotifyTypingCommand
import org.around.ieuri.community.application.conversation.provided.dto.StartConversationCommand
import org.around.ieuri.community.application.messagereport.provided.MessageReportCommandUseCase
import org.springframework.stereotype.Component

/**
 * DM 쓰기 리졸버(SPEC-0017·SPEC-0021). 전부 인증이 필요하다.
 * - 비로그인의 UnauthorizedException은 userErrors가 아니라 최상위 errors[]로 전파된다.
 */
@DgsComponent
@Component
class ConversationMutationResolver(
    private val conversationCommandUseCase: ConversationCommandUseCase,
    private val messageReportCommandUseCase: MessageReportCommandUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

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
            input.toCommand(),
            memberId,
        )
        StartConversationPayload(
            success = true,
            userErrors = emptyList(),
            conversationId = conversationId,
        )
    }

    /** 인용 스냅샷의 장소명은 **이 요청의 활성 언어로** 해소된다(SPEC-0029 AC-25.4). */
    @DgsMutation
    fun sendMessage(
        @InputArgument input: SendMessageInput,
        dfe: DgsDataFetchingEnvironment,
    ): SendMessagePayload = withCommunityUserErrors(
        onError = { SendMessagePayload(success = false, userErrors = it, messageId = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val messageId = conversationCommandUseCase.sendMessage(
            input.toCommand(),
            memberId,
            dfe.contentLanguage(),
        )
        SendMessagePayload(success = true, userErrors = emptyList(), messageId = messageId)
    }

    /** 수신자가 없어도 성공한다(AC10-2). */
    @DgsMutation
    fun notifyTyping(@InputArgument input: NotifyTypingInput): NotifyTypingPayload = withCommunityUserErrors(
        onError = { NotifyTypingPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        conversationCommandUseCase.notifyTyping(
            NotifyTypingCommand(input.conversationId),
            memberId,
        )
        NotifyTypingPayload(success = true, userErrors = emptyList())
    }

    /** 읽음 처리. 조회로는 읽음이 되지 않고 이 명시적 호출로만 된다. */
    @DgsMutation
    fun markConversationRead(
        @InputArgument input: MarkConversationReadInput,
    ): MarkConversationReadPayload = withCommunityUserErrors(
        onError = { MarkConversationReadPayload(success = false, userErrors = it, unreadCount = 0) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val remaining = conversationCommandUseCase.markConversationRead(input.toCommand(), memberId)
        MarkConversationReadPayload(
            success = true,
            userErrors = emptyList(),
            unreadCount = remaining,
        )
    }

    @DgsMutation
    fun reportMessages(@InputArgument input: ReportMessagesInput): ReportMessagesPayload = withCommunityUserErrors(
        onError = { ReportMessagesPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        messageReportCommandUseCase.reportMessages(input.toCommand(), memberId)
        ReportMessagesPayload(success = true, userErrors = emptyList())
    }
}
