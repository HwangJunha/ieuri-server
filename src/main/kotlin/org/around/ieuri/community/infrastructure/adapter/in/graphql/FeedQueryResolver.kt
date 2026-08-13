package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.application.port.`in`.query.ConversationQueryUseCase
import org.around.ieuri.community.application.port.`in`.query.FeedQueryUseCase
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.query.ConversationView
import org.around.ieuri.community.application.query.FeedItemView
import org.around.ieuri.community.application.query.MessageView
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.application.query.TypingEventView
import org.dataloader.DataLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.PostAuthor as GqlPostAuthor
import org.around.ieuri.common.graphql.generated.types.PostSpot as GqlPostSpot
import org.around.ieuri.common.graphql.generated.types.TargetCategory as GqlTargetCategory

/**
 * 피드·대화 조회 리졸버(SPEC-0017).
 *
 * **read DTO를 노드로 반환한다** — 생성 타입은 non-null 필드를 생성자 인자로 요구해 지연
 * 필드를 비워 둘 수 없다. spot·author·photoKeys는 각각 다른 저장소를 물어야 하므로
 * @DgsData 필드 리졸버가 **요청된 경우에만** 조회한다. `{ id body }`만 물으면 추가 조회는 없다.
 *
 * 참여자가 아닌 대화의 조회는 유스케이스가 ForbiddenException으로 막으며, 그 예외는 여기서
 * 잡히지 않고 최상위 errors[]로 전파된다(AC8-3).
 */
@DgsComponent
@Component
class FeedQueryResolver(
    private val feedQueryUseCase: FeedQueryUseCase,
    private val conversationQueryUseCase: ConversationQueryUseCase,
    private val memberLookupPort: MemberLookupPort,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    /** 피드. **정렬 인자가 없다** — 최신순 고정이다(R4). */
    @DgsQuery
    fun feed(
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): FeedConnection {
        val page = feedQueryUseCase.feed(first, after, currentMemberProvider.requireMemberId())
        return FeedConnection(
            edges = page.edges.map { FeedItemEdge(cursor = it.cursor, node = it.node) },
            pageInfo = page.pageInfo.toGql(),
        )
    }

    /** 차단·삭제·숨김에 걸리면 오류가 아니라 null이다(기존 `post(id:)`와 같은 관례). */
    @DgsQuery
    fun feedItem(@InputArgument id: Long): FeedItemView? =
        feedQueryUseCase.feedItem(id, currentMemberProvider.requireMemberId())

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

    // ── FeedItem 지연 필드 ────────────────────────────────────────────

    /** 사진 키. 배열 순서가 표시 순서다. 요청된 경우에만 배치로 읽는다. */
    @DgsData(parentType = "FeedItem", field = "photoKeys")
    fun feedItemPhotoKeys(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<String>> =
        dfe.loader<List<String>>(FeedItemPhotoKeysDataLoader.NAME).load(dfe.feedItemSource().id)

    @DgsData(parentType = "FeedItem", field = "likedByViewer")
    fun feedItemLikedByViewer(dfe: DgsDataFetchingEnvironment): CompletableFuture<Boolean> =
        dfe.loader<Boolean>(FeedItemLikedDataLoader.NAME)
            .load(dfe.feedItemSource().id)
            .thenApply { it ?: false }

    /**
     * 장소 칩. **피드는 spot이 필수라 non-null이다** — 조회되지 않으면 tourism 쪽 문제이며,
     * 그때도 피드 자체는 떠야 하므로(AC13-2) 대체 표기로 채운다.
     */
    @DgsData(parentType = "FeedItem", field = "spot")
    fun feedItemSpot(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostSpot> {
        val spotId = dfe.feedItemSource().spotId
        return dfe.loader<PostSpotView>(PostSpotDataLoader.NAME)
            .load(spotId)
            .thenApply { it?.toGql() ?: unresolvedSpot(spotId) }
    }

    @DgsData(parentType = "FeedItem", field = "author")
    fun feedItemAuthor(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> =
        dfe.loadAuthor(dfe.feedItemSource().authorId)

    // ── Conversation·Message 지연 필드 ───────────────────────────────

    /** 상대방. auth를 물어야 하므로 요청된 경우에만 조회한다. */
    @DgsData(parentType = "Conversation", field = "counterpart")
    fun conversationCounterpart(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> =
        dfe.loadAuthor(dfe.conversationSource().counterpartId)

    /**
     * 메시지가 인용한 피드. **방이 아니라 메시지에 붙는다** — 같은 상대와의 방은 하나인데
     * 피드는 여러 개라 방 단위 맥락은 틀린 값이 된다(인스타 스토리 답장과 같은 방식).
     *
     * 인용이 없거나 그 피드가 삭제·차단으로 안 보이면 null이고, 메시지 자체는 남는다.
     * **요청된 경우에만** 조회한다 — 목록만 그리는 화면에는 조회가 붙지 않는다.
     *
     * **조회자를 SecurityContext가 아니라 소스에서 받는다.** 구독은 이벤트마다 실행이 새로
     * 도는데 그 스레드에는 SecurityContext(ThreadLocal)가 없어서, 여기서 읽으면 인용이 달린
     * 메시지마다 인증 오류가 난다 — 인용 없는 메시지에서는 조기 반환해 드러나지도 않는다.
     */
    @DgsData(parentType = "Message", field = "feedItem")
    fun messageFeedItem(dfe: DgsDataFetchingEnvironment): FeedItemView? {
        val source = dfe.messageSource()
        val feedItemId = source.feedItemId ?: return null
        return feedQueryUseCase.feedItem(feedItemId, source.viewerId)
    }

    /**
     * 메시지 발신자. **DataLoader를 쓰지 않고 직접 읽는다.**
     *
     * DataLoader는 **한 번의 GraphQL 실행에 묶인다.** 구독은 이벤트마다 실행이 새로 도는데
     * 레지스트리는 구독이 열릴 때 만들어진 것을 계속 쓰므로, 첫 실행에서 배치가 소진된 뒤에는
     * 두 번째 이벤트의 `load()`가 영영 완료되지 않는다 — 이벤트는 도착하고 메시지 조회까지
     * 되는데 응답만 나가지 않는 형태로 멈춘다(실측: 2번째부터 member 조회가 없었다).
     *
     * 배치가 필요하지도 않다 — 이벤트 하나에 발신자는 한 명뿐이라 모을 것이 없다.
     * 회원이 없어도(탈퇴 등) 메시지는 남으므로 대체 표기로 채운다.
     */
    @DgsData(parentType = "Message", field = "sender")
    fun messageSender(dfe: DgsDataFetchingEnvironment): GqlPostAuthor {
        val senderId = dfe.messageSource().senderId
        return memberLookupPort.findAuthor(senderId).toGqlOrPlaceholder(senderId)
    }

    /** 타이핑 중인 상대방. DataLoader를 쓰지 않는 이유는 [messageSender]와 같다. */
    @DgsData(parentType = "TypingEvent", field = "sender")
    fun typingEventSender(dfe: DgsDataFetchingEnvironment): GqlPostAuthor {
        val source = dfe.getSource<TypingEventView>()
            ?: error("TypingEvent 필드 리졸버는 부모가 반환한 TypingEventView를 소스로 받아야 한다.")
        return memberLookupPort.findAuthor(source.senderId).toGqlOrPlaceholder(source.senderId)
    }

    // ── 공통 헬퍼 ────────────────────────────────────────────────────

    /** 이름이 어긋난 로더를 조용히 넘기면 필드가 빈 채로 뜬다 — 명시적으로 실패시킨다. */
    private fun <V : Any> DgsDataFetchingEnvironment.loader(name: String): DataLoader<Long, V> =
        getDataLoader<Long, V>(name) ?: error("DataLoader '$name'가 등록되지 않았다.")

    private fun DgsDataFetchingEnvironment.loadAuthor(
        memberId: Long,
    ): CompletableFuture<GqlPostAuthor> =
        loader<PostAuthorView>(PostAuthorDataLoader.NAME)
            .load(memberId)
            .thenApply { it.toGqlOrPlaceholder(memberId) }

    /**
     * 부모 리졸버가 돌려준 read DTO. `!!` 대신 명시적으로 실패시킨다 — 실제로 null이 될 수
     * 없지만 그 보장은 DGS의 실행 순서에 달려 있어 타입이 증명하지 못한다.
     */
    private fun DgsDataFetchingEnvironment.feedItemSource(): FeedItemView =
        getSource<FeedItemView>()
            ?: error("FeedItem 필드 리졸버는 부모가 반환한 FeedItemView를 소스로 받아야 한다.")

    private fun DgsDataFetchingEnvironment.conversationSource(): ConversationView =
        getSource<ConversationView>()
            ?: error("Conversation 필드 리졸버는 부모가 반환한 ConversationView를 소스로 받아야 한다.")

    private fun DgsDataFetchingEnvironment.messageSource(): MessageView =
        getSource<MessageView>()
            ?: error("Message 필드 리졸버는 부모가 반환한 MessageView를 소스로 받아야 한다.")

    private fun PostSpotView.toGql() = GqlPostSpot(
        id = id,
        title = title,
        thumbnailImageUrl = thumbnailImageUrl,
        category = category?.let { GqlTargetCategory.valueOf(it.name) },
    )

    /**
     * spot을 못 읽어도 **피드 자체는 정상 반환된다**(AC13-2). spot이 non-null이라 자리를
     * 비울 수 없으므로 id만 담은 자리표시자를 내려준다 — 클라이언트는 id로 상세를 다시 물을 수 있다.
     */
    private fun unresolvedSpot(spotId: Long) = GqlPostSpot(
        id = spotId,
        title = "",
        thumbnailImageUrl = "",
        category = null,
    )

    /**
     * 회원 정보가 없어도 피드·메시지는 남는다(탈퇴해도 상대의 화면이 깨지면 안 된다, AC17-2).
     * 작성자 표시가 비면 클라이언트가 렌더에 실패하므로 대체 표기를 내려준다.
     */
    private fun PostAuthorView?.toGqlOrPlaceholder(memberId: Long) = GqlPostAuthor(
        id = this?.id ?: memberId,
        nickname = this?.nickname ?: "알 수 없음",
        profileImageUrl = this?.profileImageUrl,
    )

    private fun CursorPageInfo.toGql() = GqlPageInfo(
        hasNextPage = hasNextPage,
        hasPreviousPage = hasPreviousPage,
        startCursor = startCursor,
        endCursor = endCursor,
    )
}
