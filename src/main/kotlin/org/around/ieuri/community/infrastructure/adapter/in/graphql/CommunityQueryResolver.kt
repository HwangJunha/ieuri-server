package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.loader
import java.util.concurrent.CompletableFuture
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.application.port.`in`.query.CommunityQueryUseCase
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.query.BlockedUsersQuery
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.community.application.query.CommentView
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toGqlOrPlaceholder
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.application.query.PostCommentsQuery
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.application.query.MyPostsQuery
import org.around.ieuri.community.application.query.PostsQuery
import org.around.ieuri.community.domain.vo.PostSortOrder
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toTargetCategories
import org.around.ieuri.common.graphql.appKey
import org.around.ieuri.community.infrastructure.locale.contentLanguage
import org.springframework.stereotype.Component
import org.around.ieuri.common.graphql.generated.types.BlockedUserConnection as GqlBlockedUserConnection
import org.around.ieuri.common.graphql.generated.types.BlockedUserEdge as GqlBlockedUserEdge
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.PostAuthor as GqlPostAuthor
import org.around.ieuri.common.graphql.generated.types.PostSortOrder as GqlPostSortOrder
import org.around.ieuri.common.graphql.generated.types.PostSpot as GqlPostSpot
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
    private val memberLookupPort: MemberLookupPort,
    private val currentMemberProvider: CurrentMemberProvider,
    private val eventPublisher: CommunityEventPublisherPort,
) {

    /**
     * 글 피드. 인자 조합이 범위를 정한다 — 대상 하나, 좌표 반경, 또는 전체다.
     */
    @DgsQuery
    fun posts(
        @InputArgument catalogId: Long?,
        @InputArgument latitude: Double?,
        @InputArgument longitude: Double?,
        @InputArgument radiusMeters: Int?,
        @InputArgument categories: List<GqlTargetCategory>?,
        @InputArgument tag: String?,
        @InputArgument sort: GqlPostSortOrder?,
        @InputArgument hiddenByViewer: Boolean?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
        dfe: DgsDataFetchingEnvironment,
    ): PostConnection {
        val memberId = currentMemberProvider.requireMemberId()
        // **좌표를 실제로 보낸 호출만** 남긴다(SPEC-0032 R19) — 이 연산은 좌표가 선택이라
        // 전체 피드 조회는 위치 조회가 아니다. 좌표 자체는 넘기지 않는다.
        if (latitude != null && longitude != null) {
            eventPublisher.publish(LocationAccessed(memberId, "posts"))
        }
        val page = communityQueryUseCase.posts(
            PostsQuery(
                catalogId = catalogId,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                categories = toTargetCategories(categories),
                tag = tag,
                // 스키마 기본값이 LATEST라 null이 오는 경우는 없지만, 계약을 코드에도 남긴다.
                sort = sort?.let { PostSortOrder.valueOf(it.name) } ?: PostSortOrder.LATEST,
                hiddenByViewer = hiddenByViewer ?: false,
                first = first ?: DEFAULT_PAGE_SIZE,
                after = after,
            ),
            memberId = memberId,
            language = dfe.contentLanguage(),
        )
        return PostConnection(
            edges = page.edges.map { PostEdge(cursor = it.cursor, node = it.node) },
            pageInfo = page.pageInfo.toGql(),
        )
    }

    /**
     * 내가 쓴 글(SPEC-0011 R3). 위치·반경 인자가 없고 숨김·차단 필터도 걸리지 않는다 —
     * 자기 글은 자기가 숨겼더라도 마이페이지에 보여야 한다.
     */
    @DgsQuery
    fun myPosts(
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): PostConnection {
        val memberId = currentMemberProvider.requireMemberId()
        val page = communityQueryUseCase.myPosts(
            MyPostsQuery(first = first ?: DEFAULT_PAGE_SIZE, after = after),
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
        // 목록 자체가 회원 정보를 보여주기 위한 것이라 지연시키지 않는다. 대신 **한 번에** 읽는다.
        val authors = memberLookupPort.findAuthors(page.edges.map { it.node.member.id })
        return GqlBlockedUserConnection(
            edges = page.edges.map {
                val memberId = it.node.member.id
                GqlBlockedUserEdge(
                    cursor = it.cursor,
                    node = authors[memberId].toGqlOrPlaceholder(memberId),
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
    fun communityChips(dfe: DgsDataFetchingEnvironment): List<GqlTargetCategory> =
        communityQueryUseCase.communityChips(dfe.appKey(), dfe.contentLanguage())
            .map { GqlTargetCategory.valueOf(it.category.name) }

    /** 사용자가 입력한 태그. 별도 테이블이라 요청된 경우에만 배치로 읽는다. */
    @DgsData(parentType = "Post", field = "tags")
    fun postTags(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<String>> =
        dfe.loader<List<String>>(PostTagsDataLoader.NAME).load(dfe.postSource().id)

    /** 사진 키. 배열 순서가 표시 순서다. */
    @DgsData(parentType = "Post", field = "photoKeys")
    fun postPhotoKeys(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<String>> =
        dfe.loader<List<String>>(PostPhotoKeysDataLoader.NAME).load(dfe.postSource().id)

    /** 글의 카테고리. 연결 테이블에 있어 요청된 경우에만 읽는다. */
    @DgsData(parentType = "Post", field = "category")
    fun postCategory(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlTargetCategory> {
        val postId = dfe.postSource().id
        return dfe.loader<TargetCategory>(PostCategoryDataLoader.NAME)
            .load(postId)
            .thenApply {
                requireNotNull(it) { "글($postId)에 카테고리 연결이 없다." }.toGql()
            }
    }

    /**
     * 호출자가 좋아요했는지.
     *
     * **뷰어를 여기서 확정해 실어 보낸다**(SPEC-0032 R27) — 로더가 자기 스레드에서 읽으면
     * 가상 스레드 전환에 신원을 잃는다.
     */
    @DgsData(parentType = "Post", field = "likedByViewer")
    fun postLikedByViewer(dfe: DgsDataFetchingEnvironment): CompletableFuture<Boolean> =
        dfe.loader<Boolean>(PostLikedDataLoader.NAME)
            .load(dfe.postSource().id, ViewerId(currentMemberProvider.requireMemberId()))
            .thenApply { it ?: false }

    /**
     * 장소 칩. **장소는 선택이라 없으면 null이다.** 연결을 먼저 배치로 확인하고, 있을 때만
     * tourism을 배치로 묻는다 — 두 단계 모두 요청된 경우에만 돈다.
     *
     * **활성 언어에서 해소되지 않아도 null이다**(SPEC-0029 R-27). 다른 언어 이름으로 대신
     * 채우지 않는다 — 클라이언트는 칩이 없는 것으로 렌더한다. 목록 자체가 활성 언어로
     * 걸러지므로(R-23) 정상 경로에서는 여기가 null이 될 일이 거의 없다.
     */
    @DgsData(parentType = "Post", field = "spot")
    fun postSpot(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostSpot?> =
        dfe.loader<Long>(PostCatalogIdDataLoader.NAME)
            .load(dfe.postSource().id)
            .thenCompose { catalogId ->
                if (catalogId == null) {
                    CompletableFuture.completedFuture(null)
                } else {
                    dfe.loader<PostSpotView>(PostSpotDataLoader.NAME)
                        .load(catalogId, dfe.contentLanguage())
                        .thenApply { it?.toGql() }
                }
            }

    /** 작성자. auth를 물어야 하므로 요청된 경우에만 조회한다. */
    @DgsData(parentType = "Post", field = "author")
    fun postAuthor(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> =
        dfe.loadAuthor(dfe.postSource().authorId)

    /**
     * 답글. **요청된 경우에만** 배치로 읽는다 — 목록만 그리는 화면은 조회가 붙지 않는다.
     * 답글의 replies는 항상 빈 목록이다(깊이 2단계).
     */
    @DgsData(parentType = "Comment", field = "replies")
    fun commentReplies(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<CommentView>> {
        val source = dfe.getSource<CommentView>()
            ?: error("Comment 필드 리졸버는 부모가 반환한 CommentView를 소스로 받아야 한다.")
        return dfe.loader<List<CommentView>>(CommentReplyDataLoader.NAME)
            .load(source.id, ViewerId(currentMemberProvider.requireMemberId()))
    }

    @DgsData(parentType = "Comment", field = "author")
    fun commentAuthor(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> {
        val source = dfe.getSource<CommentView>()
            ?: error("Comment 필드 리졸버는 부모가 반환한 CommentView를 소스로 받아야 한다.")
        return dfe.loadAuthor(source.author.id)
    }

    /**
     * 작성자를 배치로 읽는다. 글과 댓글이 같은 로더를 쓰므로 같은 사람이면 조회가 합쳐진다.
     * 회원 정보가 없어도 글은 남으므로(탈퇴해도 글은 보존한다) 대체 표기로 채운다.
     */
    private fun DgsDataFetchingEnvironment.loadAuthor(memberId: Long): CompletableFuture<GqlPostAuthor> =
        loader<PostAuthorView>(PostAuthorDataLoader.NAME)
            .load(memberId)
            .thenApply { it.toGqlOrPlaceholder(memberId) }

    /**
     * 댓글 미리보기. **DataLoader가 글 id를 모아 한 번에 읽는다** — 카드마다 물으면
     * 피드 한 페이지에 쿼리가 20번 붙는다.
     *
     * 여기에 페이지네이션은 없다. 전체 목록과 더 보기는 루트의 `comments(postId:)`다.
     */
    @DgsData(parentType = "Post", field = "commentPreview")
    fun commentPreview(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<CommentView>> =
        dfe.loader<List<CommentView>>(CommentPreviewDataLoader.NAME)
            .load(dfe.postSource().id, ViewerId(currentMemberProvider.requireMemberId()))

    /**
     * 부모 리졸버가 돌려준 read DTO. `!!` 대신 명시적으로 실패시킨다 — 실제로 null이 될 수
     * 없지만 그 보장은 DGS의 실행 순서에 달려 있어 타입이 증명하지 못한다.
     */
    private fun DgsDataFetchingEnvironment.postSource(): PostView =
        getSource<PostView>()
            ?: error("Post 필드 리졸버는 부모가 반환한 PostView를 소스로 받아야 한다.")

    private fun TargetCategory.toGql() = GqlTargetCategory.valueOf(name)

    private fun PostSpotView.toGql() = GqlPostSpot(
        id = id,
        title = title,
        thumbnailImageUrl = thumbnailImageUrl,
        category = category?.let { GqlTargetCategory.valueOf(it.name) },
    )

    // 작성자 대체 표기와 pageInfo 변환은 mapper 패키지가 소유한다 — 장소 질문/답변도
    // 같은 모양을 쓰므로, 리졸버마다 두면 한 곳만 고쳐지는 날이 온다(POL-033).

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_RADIUS_METERS = 20_000
    }
}
