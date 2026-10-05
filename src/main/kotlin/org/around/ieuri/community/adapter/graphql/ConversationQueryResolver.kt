package org.around.ieuri.community.adapter.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import graphql.language.OperationDefinition
import org.around.ieuri.common.graphql.loader
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.adapter.graphql.mapper.toGql
import org.around.ieuri.community.adapter.graphql.mapper.toGqlOrPlaceholder
import org.around.ieuri.community.application.conversation.provided.ConversationQueryUseCase
import org.around.ieuri.community.application.conversation.provided.dto.ConversationView
import org.around.ieuri.community.application.conversation.provided.dto.MessageView
import org.around.ieuri.community.application.conversation.provided.dto.TypingEventView
import org.around.ieuri.community.application.shared.dto.PostAuthorView
import org.around.ieuri.community.application.shared.required.MemberLookupPort
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import org.around.ieuri.common.graphql.generated.types.ConversationSpotQaOrigin as GqlConversationSpotQaOrigin
import org.around.ieuri.common.graphql.generated.types.PostAuthor as GqlPostAuthor

/**
 * 대화 조회 리졸버(SPEC-0017). read DTO를 노드로 반환하고 counterpart·sender는 요청 시에만 읽는다.
 * - 참여자가 아닌 조회의 ForbiddenException은 잡지 않고 최상위 errors[]로 전파한다(AC8-3).
 */
@DgsComponent
@Component
class ConversationQueryResolver(
    private val conversationQueryUseCase: ConversationQueryUseCase,
    private val memberLookupPort: MemberLookupPort,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    @DgsQuery
    fun conversations(
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): ConversationConnection {
        val page = conversationQueryUseCase.conversations(
            first,
            after,
            currentMemberProvider.requireMemberId(),
        )
        return ConversationConnection(
            edges = page.edges.map { ConversationEdge(cursor = it.cursor, node = it.node) },
            pageInfo = page.pageInfo.toGql(),
        )
    }

    /** 참여자가 아니면 유스케이스가 FORBIDDEN으로 막는다 — 여기서 잡지 않는다(AC8-3). */
    @DgsQuery
    fun conversationMessages(
        @InputArgument conversationId: Long,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): MessageConnection {
        val page = conversationQueryUseCase.conversationMessages(
            conversationId,
            first,
            after,
            currentMemberProvider.requireMemberId(),
        )
        return MessageConnection(
            edges = page.edges.map { MessageEdge(cursor = it.cursor, node = it.node) },
            pageInfo = page.pageInfo.toGql(),
        )
    }

    // ── Conversation·Message 지연 필드 ───────────────────────────────

    /** 상대방. auth를 물어야 하므로 요청된 경우에만 조회한다. */
    @DgsData(parentType = "Conversation", field = "counterpart")
    fun conversationCounterpart(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> = dfe.loadAuthor(dfe.conversationSource().counterpartId)

    /** 인용 스냅샷(SPEC-0026 R14). 추가 조회 없이 read DTO 값을 응답 타입으로 옮긴다. */
    @DgsData(parentType = "Message", field = "spotQaOrigin")
    fun messageSpotQaOrigin(
        dfe: DgsDataFetchingEnvironment,
    ): GqlConversationSpotQaOrigin? = dfe.messageSource().spotQaOrigin?.toGql()

    /**
     * 메시지 발신자. 구독이면 직접, 쿼리면 배치로 읽는다(SPEC-0032 R26).
     * - 구독에서 DataLoader를 쓰면 두 번째 이벤트부터 `load()`가 완료되지 않아 응답이 멈춘다.
     * - 회원이 없으면(탈퇴) 대체 표기로 채운다.
     */
    @DgsData(parentType = "Message", field = "sender")
    fun messageSender(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> = dfe.loadSender(dfe.messageSource().senderId)

    /** 타이핑 중인 상대방. 읽는 방식은 [messageSender]와 같다. */
    @DgsData(parentType = "TypingEvent", field = "sender")
    fun typingEventSender(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> {
        val source = dfe.getSource<TypingEventView>()
            ?: error("TypingEvent 필드 리졸버는 부모가 반환한 TypingEventView를 소스로 받아야 한다.")
        return dfe.loadSender(source.senderId)
    }

    /** 발신자를 읽는다. 구독 판정은 소켓 여부가 아니라 실행 중인 오퍼레이션 타입으로 한다. */
    private fun DgsDataFetchingEnvironment.loadSender(senderId: Long): CompletableFuture<GqlPostAuthor> {
        if (isSubscription()) {
            return CompletableFuture.completedFuture(
                memberLookupPort.findAuthor(senderId).toGqlOrPlaceholder(senderId),
            )
        }
        return loadAuthor(senderId)
    }

    private fun DgsDataFetchingEnvironment.isSubscription(): Boolean = operationDefinition?.operation == OperationDefinition.Operation.SUBSCRIPTION

    // ── 공통 헬퍼 ────────────────────────────────────────────────────

    private fun DgsDataFetchingEnvironment.loadAuthor(
        memberId: Long,
    ): CompletableFuture<GqlPostAuthor> = loader<PostAuthorView>(PostAuthorDataLoader.NAME)
        .load(memberId)
        .thenApply { it.toGqlOrPlaceholder(memberId) }

    /** 부모 리졸버가 돌려준 read DTO. 없으면 명시적으로 실패한다. */
    private fun DgsDataFetchingEnvironment.conversationSource(): ConversationView = getSource<ConversationView>()
        ?: error("Conversation 필드 리졸버는 부모가 반환한 ConversationView를 소스로 받아야 한다.")

    private fun DgsDataFetchingEnvironment.messageSource(): MessageView = getSource<MessageView>()
        ?: error("Message 필드 리졸버는 부모가 반환한 MessageView를 소스로 받아야 한다.")
}
