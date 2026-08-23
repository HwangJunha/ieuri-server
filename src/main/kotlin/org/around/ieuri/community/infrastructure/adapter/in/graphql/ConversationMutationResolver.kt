package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

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
import org.around.ieuri.community.application.command.NotifyTypingCommand
import org.around.ieuri.community.application.command.StartConversationCommand
import org.around.ieuri.community.application.port.`in`.command.ConversationCommandUseCase
import org.around.ieuri.community.application.port.`in`.command.MessageReportCommandUseCase
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toCommand
import org.around.ieuri.community.infrastructure.locale.contentLanguage
import org.springframework.stereotype.Component

/**
 * DM 쓰기 리졸버(SPEC-0017·SPEC-0021). 전부 인증이 필요하며, 비로그인은 UnauthorizedException이
 * withCommunityUserErrors에 붙잡히지 않고 최상위 errors[]로 전파된다.
 *
 * 리졸버는 얇게 둔다 — 입력을 커맨드로 옮기고 결과를 payload로 옮길 뿐 판단하지 않는다.
 * 입력→커맨드 변환은 필드가 많은 것만 확장 함수로 뺐다(POL-033) — 나머지는 인자 하나라
 * 뒤바뀔 여지가 없다.
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
    ): SendMessagePayload =
        withCommunityUserErrors(
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

    /**
     * 읽음 처리. **조회가 아니라 명시적 호출로만 읽음이 된다** — 목록 미리보기나 알림에서
     * 스쳐 본 것이 읽음으로 처리되면 사용자가 메시지를 놓친다.
     */
    @DgsMutation
    fun markConversationRead(
        @InputArgument input: MarkConversationReadInput,
    ): MarkConversationReadPayload =
        withCommunityUserErrors(
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
    fun reportMessages(@InputArgument input: ReportMessagesInput): ReportMessagesPayload =
        withCommunityUserErrors(
            onError = { ReportMessagesPayload(success = false, userErrors = it) },
        ) {
            val memberId = currentMemberProvider.requireMemberId()
            messageReportCommandUseCase.reportMessages(input.toCommand(), memberId)
            ReportMessagesPayload(success = true, userErrors = emptyList())
        }
}
