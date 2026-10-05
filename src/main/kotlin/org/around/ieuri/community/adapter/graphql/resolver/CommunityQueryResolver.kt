package org.around.ieuri.community.adapter.graphql.resolver

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.common.graphql.appKey
import org.around.ieuri.common.graphql.loader
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.adapter.graphql.connection.CommentConnection
import org.around.ieuri.community.adapter.graphql.connection.CommentEdge
import org.around.ieuri.community.adapter.graphql.connection.PostConnection
import org.around.ieuri.community.adapter.graphql.connection.PostEdge
import org.around.ieuri.community.adapter.graphql.dataloader.CommentPreviewDataLoader
import org.around.ieuri.community.adapter.graphql.dataloader.CommentReplyDataLoader
import org.around.ieuri.community.adapter.graphql.dataloader.PostAuthorDataLoader
import org.around.ieuri.community.adapter.graphql.dataloader.PostCatalogIdDataLoader
import org.around.ieuri.community.adapter.graphql.dataloader.PostCategoryDataLoader
import org.around.ieuri.community.adapter.graphql.dataloader.PostLikedDataLoader
import org.around.ieuri.community.adapter.graphql.dataloader.PostPhotoKeysDataLoader
import org.around.ieuri.community.adapter.graphql.dataloader.PostSpotDataLoader
import org.around.ieuri.community.adapter.graphql.dataloader.PostTagsDataLoader
import org.around.ieuri.community.adapter.graphql.dataloader.ViewerId
import org.around.ieuri.community.adapter.graphql.mapper.toGql
import org.around.ieuri.community.adapter.graphql.mapper.toGqlOrPlaceholder
import org.around.ieuri.community.adapter.graphql.mapper.toTargetCategories
import org.around.ieuri.community.adapter.graphql.support.contentLanguage
import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.around.ieuri.community.application.comment.provided.CommentQueryUseCase
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.around.ieuri.community.application.shared.dto.BlockedUsersQuery
import org.around.ieuri.community.application.shared.dto.CommentView
import org.around.ieuri.community.application.shared.dto.MyPostsQuery
import org.around.ieuri.community.application.shared.dto.PostAuthorView
import org.around.ieuri.community.application.shared.dto.PostCommentsQuery
import org.around.ieuri.community.application.shared.dto.PostSpotView
import org.around.ieuri.community.application.shared.dto.PostView
import org.around.ieuri.community.application.shared.dto.PostsQuery
import org.around.ieuri.community.application.shared.required.CommunityEventPublisherPort
import org.around.ieuri.community.application.shared.required.MemberLookupPort
import org.around.ieuri.community.domain.shared.PostSortOrder
import org.around.ieuri.community.domain.shared.TargetCategory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import org.around.ieuri.common.graphql.generated.types.BlockedUserConnection as GqlBlockedUserConnection
import org.around.ieuri.common.graphql.generated.types.BlockedUserEdge as GqlBlockedUserEdge
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.PostAuthor as GqlPostAuthor
import org.around.ieuri.common.graphql.generated.types.PostSortOrder as GqlPostSortOrder
import org.around.ieuri.common.graphql.generated.types.PostSpot as GqlPostSpot
import org.around.ieuri.common.graphql.generated.types.TargetCategory as GqlTargetCategory

/**
 * 커뮤니티 조회 리졸버. read DTO를 노드로 반환하고 spot·author·comments 등은 @DgsData가 요청 시에만 읽는다.
 * - 커넥션 타입 이름은 `Connection`으로 끝나야 Spring for GraphQL 커넥션 래핑을 통과한다.
 */
@DgsComponent
@Component
class CommunityQueryResolver(
    private val blockQueryUseCase: BlockQueryUseCase,
    private val commentQueryUseCase: CommentQueryUseCase,
    private val postQueryUseCase: PostQueryUseCase,
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
        // 좌표를 보낸 호출만 위치 조회로 남긴다(SPEC-0032 R19). 좌표 값은 싣지 않는다.
        if (latitude != null && longitude != null) {
            eventPublisher.publish(LocationAccessed(memberId, "posts"))
        }
        val page = postQueryUseCase.posts(
            PostsQuery(
                catalogId = catalogId,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                categories = toTargetCategories(categories),
                tag = tag,
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

    /** 내가 쓴 글(SPEC-0011 R3). 숨김·차단 필터가 걸리지 않는다. */
    @DgsQuery
    fun myPosts(
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): PostConnection {
        val memberId = currentMemberProvider.requireMemberId()
        val page = postQueryUseCase.myPosts(
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
        val page = commentQueryUseCase.comments(
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
    fun post(@InputArgument id: Long): PostView? = postQueryUseCase.post(id, currentMemberProvider.requireMemberId())

    @DgsQuery
    fun blockedUsers(
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlBlockedUserConnection {
        val memberId = currentMemberProvider.requireMemberId()
        val page = blockQueryUseCase.blockedUsers(
            BlockedUsersQuery(first = first ?: DEFAULT_PAGE_SIZE, after = after),
            memberId = memberId,
        )
        // 회원 정보가 목록의 본체라 지연시키지 않고 한 번에 읽는다.
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

    /** 상단 카테고리 칩. 앱 식별은 인자가 아니라 X-App-Key 헤더에서 온다. */
    @DgsQuery
    fun communityChips(dfe: DgsDataFetchingEnvironment): List<GqlTargetCategory> = postQueryUseCase.communityChips(dfe.appKey(), dfe.contentLanguage())
        .map { GqlTargetCategory.valueOf(it.category.name) }

    /** 사용자가 입력한 태그. 별도 테이블이라 요청된 경우에만 배치로 읽는다. */
    @DgsData(parentType = "Post", field = "tags")
    fun postTags(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<String>> = dfe.loader<List<String>>(PostTagsDataLoader.NAME).load(dfe.postSource().id)

    /** 사진 키. 배열 순서가 표시 순서다. */
    @DgsData(parentType = "Post", field = "photoKeys")
    fun postPhotoKeys(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<String>> = dfe.loader<List<String>>(PostPhotoKeysDataLoader.NAME).load(dfe.postSource().id)

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
     * - 뷰어는 여기서 확정해 로더에 넘긴다 — 로더 스레드에서 읽으면 신원을 잃는다(SPEC-0032 R27).
     */
    @DgsData(parentType = "Post", field = "likedByViewer")
    fun postLikedByViewer(dfe: DgsDataFetchingEnvironment): CompletableFuture<Boolean> = dfe.loader<Boolean>(PostLikedDataLoader.NAME)
        .load(dfe.postSource().id, ViewerId(currentMemberProvider.requireMemberId()))
        .thenApply { it ?: false }

    /**
     * 장소 칩. 연결을 먼저 확인하고 있을 때만 tourism을 배치로 묻는다.
     * - 장소가 없거나 활성 언어에서 해소되지 않으면 null이다. 다른 언어 이름으로 채우지 않는다(SPEC-0029 R-27).
     */
    @DgsData(parentType = "Post", field = "spot")
    fun postSpot(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostSpot?> = dfe.loader<Long>(PostCatalogIdDataLoader.NAME)
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
    fun postAuthor(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> = dfe.loadAuthor(dfe.postSource().authorId)

    /** 답글. 요청된 경우에만 배치로 읽는다. 답글의 replies는 항상 빈 목록이다(깊이 2단계). */
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

    /** 작성자를 배치로 읽는다. 회원 정보가 없으면(탈퇴) 대체 표기로 채운다. */
    private fun DgsDataFetchingEnvironment.loadAuthor(memberId: Long): CompletableFuture<GqlPostAuthor> = loader<PostAuthorView>(PostAuthorDataLoader.NAME)
        .load(memberId)
        .thenApply { it.toGqlOrPlaceholder(memberId) }

    /** 댓글 미리보기. 글 id를 모아 배치로 읽으며 페이지네이션은 없다(전체는 `comments(postId:)`). */
    @DgsData(parentType = "Post", field = "commentPreview")
    fun commentPreview(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<CommentView>> = dfe.loader<List<CommentView>>(CommentPreviewDataLoader.NAME)
        .load(dfe.postSource().id, ViewerId(currentMemberProvider.requireMemberId()))

    /** 부모 리졸버가 돌려준 read DTO. 없으면 명시적으로 실패한다. */
    private fun DgsDataFetchingEnvironment.postSource(): PostView = getSource<PostView>()
        ?: error("Post 필드 리졸버는 부모가 반환한 PostView를 소스로 받아야 한다.")

    private fun TargetCategory.toGql() = GqlTargetCategory.valueOf(name)

    private fun PostSpotView.toGql() = GqlPostSpot(
        id = id,
        title = title,
        thumbnailImageUrl = thumbnailImageUrl,
        category = category?.let { GqlTargetCategory.valueOf(it.name) },
    )

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_RADIUS_METERS = 20_000
    }
}
