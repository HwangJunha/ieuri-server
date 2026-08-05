package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import java.util.concurrent.CompletableFuture
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.application.port.`in`.query.CommunityQueryUseCase
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.query.BlockedUsersQuery
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.community.application.query.CommentView
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.application.query.PostCommentsQuery
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.application.query.PostsQuery
import org.around.ieuri.community.domain.vo.PostTarget
import org.around.ieuri.community.domain.vo.PostTargetType
import org.around.ieuri.community.domain.vo.PostTopic
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.infrastructure.app.AppKeyContext
import org.around.ieuri.community.infrastructure.locale.CommunityLocaleContext
import org.springframework.stereotype.Component
import org.around.ieuri.common.graphql.generated.types.BlockedUserConnection as GqlBlockedUserConnection
import org.around.ieuri.common.graphql.generated.types.BlockedUserEdge as GqlBlockedUserEdge
import org.around.ieuri.common.graphql.generated.types.CommunityChip as GqlCommunityChip
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.PostAuthor as GqlPostAuthor
import org.around.ieuri.common.graphql.generated.types.PostTarget as GqlPostTarget
import org.around.ieuri.common.graphql.generated.types.PostTargetType as GqlPostTargetType
import org.around.ieuri.common.graphql.generated.types.PostTopic as GqlPostTopic
import org.around.ieuri.common.graphql.generated.types.TargetCategory as GqlTargetCategory

/**
 * 커뮤니티 조회 리졸버.
 *
 * **read DTO를 노드로 반환한다** — 생성 타입은 non-null 필드를 생성자 필수 인자로 요구해서
 * 지연 필드를 비워 둘 수 없다. spot·author·comments는 각각 다른 저장소를 물어야 하므로
 * @DgsData 필드 리졸버가 **요청된 경우에만** 조회한다. `{ id body }`만 물으면 추가 조회는 없다.
 *
 * 글·댓글 커넥션은 생성 타입 대신 이 패키지의 [PostConnection]·[CommentConnection]을 쓴다 —
 * 생성된 edge의 node가 지연 필드를 비워 둘 수 없기 때문이다. 두 타입의 **이름이 `Connection`으로
 * 끝나야** Spring for GraphQL의 커넥션 래핑을 통과한다(CommunityConnections.kt 참조).
 */
@DgsComponent
@Component
class CommunityQueryResolver(
    private val communityQueryUseCase: CommunityQueryUseCase,
    private val spotLookupPort: SpotLookupPort,
    private val memberLookupPort: MemberLookupPort,
    private val currentMemberProvider: CurrentMemberProvider,
    private val appKeyContext: AppKeyContext,
    private val localeContext: CommunityLocaleContext,
) {

    /**
     * 글 피드. 인자 조합이 범위를 정한다 — 대상 하나, 좌표 반경, 또는 전체다.
     */
    @DgsQuery
    fun posts(
        @InputArgument targetType: GqlPostTargetType?,
        @InputArgument targetId: Long?,
        @InputArgument latitude: Double?,
        @InputArgument longitude: Double?,
        @InputArgument radiusMeters: Int?,
        @InputArgument category: GqlTargetCategory?,
        @InputArgument topic: GqlPostTopic?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): PostConnection {
        val memberId = currentMemberProvider.requireMemberId()
        // 종류와 id가 함께여야 대상 하나를 가리킨다 — 하나만 주면 대상 지정이 아니다.
        val target = if (targetType != null && targetId != null) {
            PostTarget(PostTargetType.valueOf(targetType.name), targetId)
        } else {
            null
        }

        val page = communityQueryUseCase.posts(
            PostsQuery(
                target = target,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                category = category?.let { TargetCategory.valueOf(it.name) },
                topic = topic?.let { PostTopic.valueOf(it.name) },
                first = first ?: DEFAULT_PAGE_SIZE,
                after = after,
            ),
            memberId = memberId,
        )
        return PostConnection(
            edges = page.edges.map { PostEdge(cursor = it.cursor, node = it.node) },
            pageInfo = page.pageInfo.toGql(),
        )
    }

    /** 글의 댓글 목록. 미리보기와 달리 페이지네이션이 있다. */
    @DgsQuery
    fun comments(
        @InputArgument postId: Long,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): CommentConnection {
        val page = communityQueryUseCase.comments(
            PostCommentsQuery(
                postId = postId,
                first = first ?: DEFAULT_PAGE_SIZE,
                after = after,
            ),
            memberId = currentMemberProvider.requireMemberId(),
        )
        return CommentConnection(
            edges = page.edges.map { CommentEdge(cursor = it.cursor, node = it.node) },
            pageInfo = page.pageInfo.toGql(),
        )
    }

    /** 차단·숨김에 걸리면 오류가 아니라 null이다 — 통상적인 부재로 다룬다. */
    @DgsQuery
    fun post(@InputArgument id: Long): PostView? =
        communityQueryUseCase.post(id, currentMemberProvider.requireMemberId())

    @DgsQuery
    fun blockedUsers(
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlBlockedUserConnection {
        val memberId = currentMemberProvider.requireMemberId()
        val page = communityQueryUseCase.blockedUsers(
            BlockedUsersQuery(first = first ?: DEFAULT_PAGE_SIZE, after = after),
            memberId = memberId,
        )
        return GqlBlockedUserConnection(
            edges = page.edges.map {
                GqlBlockedUserEdge(
                    cursor = it.cursor,
                    // 차단 목록의 노드는 회원 표시 정보라 여기서 채운다 — 목록 자체가
                    // 그 정보를 보여주기 위한 것이므로 지연시킬 이유가 없다.
                    node = memberLookupPort.findAuthor(it.node.member.id).toGqlOrPlaceholder(it.node.member.id),
                    blockedAt = it.node.blockedAt.toString(),
                )
            },
            pageInfo = page.pageInfo.toGql(),
        )
    }

    /**
     * 상단 카테고리 칩. 앱 식별은 X-App-Key 헤더에서 오며 GraphQL 인자가 아니다 —
     * 클라이언트가 고르는 값이 아니라 요청의 속성이기 때문이다(Accept-Language와 같은 이유).
     */
    @DgsQuery
    fun communityChips(): List<GqlCommunityChip> =
        communityQueryUseCase.communityChips(appKeyContext.appKey, localeContext.language).map {
            GqlCommunityChip(
                category = GqlTargetCategory.valueOf(it.category.name),
                position = it.position,
            )
        }

    /** 대상 칩. 상대 도메인을 물어야 하므로 **요청된 경우에만** 조회한다. */
    @DgsData(parentType = "Post", field = "target")
    fun postTarget(dfe: DgsDataFetchingEnvironment): GqlPostTarget? {
        val source = dfe.postSource()
        val summary = spotLookupPort.findSummary(source.target.id) ?: return null
        return GqlPostTarget(
            type = GqlPostTargetType.valueOf(summary.type.name),
            id = summary.id,
            title = summary.title,
            thumbnailImageUrl = summary.thumbnailImageUrl,
            category = summary.category?.let { GqlTargetCategory.valueOf(it.name) },
        )
    }

    /** 작성자. auth를 물어야 하므로 요청된 경우에만 조회한다. */
    @DgsData(parentType = "Post", field = "author")
    fun postAuthor(dfe: DgsDataFetchingEnvironment): GqlPostAuthor {
        val source = dfe.postSource()
        return memberLookupPort.findAuthor(source.author.id).toGqlOrPlaceholder(source.author.id)
    }

    @DgsData(parentType = "Comment", field = "author")
    fun commentAuthor(dfe: DgsDataFetchingEnvironment): GqlPostAuthor {
        val source = dfe.getSource<CommentView>()
            ?: error("Comment 필드 리졸버는 부모가 반환한 CommentView를 소스로 받아야 한다.")
        return memberLookupPort.findAuthor(source.author.id).toGqlOrPlaceholder(source.author.id)
    }

    /**
     * 댓글 미리보기. **DataLoader가 글 id를 모아 한 번에 읽는다** — 카드마다 물으면
     * 피드 한 페이지에 쿼리가 20번 붙는다.
     *
     * 여기에 페이지네이션은 없다. 전체 목록과 더 보기는 루트의 `comments(postId:)`다.
     */
    @DgsData(parentType = "Post", field = "commentPreview")
    fun commentPreview(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<CommentView>> {
        // 등록된 로더가 없으면 이름이 어긋난 것이다 — 조용히 null을 돌려주면 카드가 빈 채로 뜬다.
        val loader = dfe.getDataLoader<Long, List<CommentView>>(CommentPreviewDataLoader.NAME)
            ?: error("DataLoader '${CommentPreviewDataLoader.NAME}'가 등록되지 않았다.")
        return loader.load(dfe.postSource().id)
    }

    /**
     * 부모 리졸버가 돌려준 read DTO. `!!` 대신 명시적으로 실패시킨다 — 실제로 null이 될 수
     * 없지만 그 보장은 DGS의 실행 순서에 달려 있어 타입이 증명하지 못한다.
     */
    private fun DgsDataFetchingEnvironment.postSource(): PostView =
        getSource<PostView>()
            ?: error("Post 필드 리졸버는 부모가 반환한 PostView를 소스로 받아야 한다.")

    /**
     * 회원 정보가 없어도 글은 남는다(탈퇴해도 글·댓글은 보존한다). 작성자 표시가 비면
     * 클라이언트가 렌더에 실패하므로 대체 표기를 내려준다.
     */
    private fun PostAuthorView?.toGqlOrPlaceholder(
        memberId: Long,
    ): GqlPostAuthor = GqlPostAuthor(
        id = this?.id ?: memberId,
        nickname = this?.nickname ?: WITHDRAWN_NICKNAME,
        profileImageUrl = this?.profileImageUrl,
    )

    private fun CursorPageInfo.toGql() = GqlPageInfo(
        hasNextPage = hasNextPage,
        hasPreviousPage = hasPreviousPage,
        startCursor = startCursor,
        endCursor = endCursor,
    )

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_RADIUS_METERS = 20_000
        /** 탈퇴 회원의 표시명. 글은 보존되지만 프로필은 사라진다. */
        const val WITHDRAWN_NICKNAME = "알 수 없음"
    }
}
