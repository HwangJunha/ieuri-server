package org.around.ieuri.community.application.service

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.event.PostViewed
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.`in`.query.CommunityQueryUseCase
import org.around.ieuri.community.application.port.out.client.CategoryConfigPort
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.port.out.query.BlockedUsersSearch
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.MyPostsSearch
import org.around.ieuri.community.application.port.out.query.PostCommentsSearch
import org.around.ieuri.community.application.port.out.query.PostSpatialQueryPort
import org.around.ieuri.community.application.port.out.query.PostsArea
import org.around.ieuri.community.application.port.out.query.PostsSearch
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.BlockedUserView
import org.around.ieuri.community.application.query.BlockedUsersQuery
import org.around.ieuri.community.application.query.CommentView
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.application.query.MyPostsQuery
import org.around.ieuri.community.application.query.PostCommentsQuery
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.application.query.PostsQuery
import org.around.ieuri.community.domain.post.PostTag
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.PostSortOrder
import org.around.ieuri.community.domain.vo.TargetCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 커뮤니티 조회. 애그리게이트를 거치지 않고 read DTO를 받는다.
 * - 피드·단건·댓글은 모두 [viewerContext]를 거친다 — 한 경로라도 빠지면 차단·숨김이 무력화된다.
 */
@Service
@Transactional(readOnly = true)
class CommunityQueryService(
    private val communityQueryPort: CommunityQueryPort,
    private val postSpatialQueryPort: PostSpatialQueryPort,
    private val categoryConfigPort: CategoryConfigPort,
    private val eventPublisher: CommunityEventPublisherPort,
    private val policy: CommunityPolicy,
) : CommunityQueryUseCase {

    /** 글 피드. 인자 조합이 범위(장소 하나·좌표 반경·카테고리·전체)를 정한다. */
    override fun posts(query: PostsQuery, memberId: Long, language: ContentLanguage): CursorPage<PostView> {
        val search = PostsSearch(
            catalogId = query.catalogId,
            area = resolveArea(query),
            categories = query.categories,
            // 대소문자·선행 #을 가리지 않도록 정규화한다.
            normalizedTag = query.tag?.let { PostTag.parseOrNull(it)?.normalized },
            sort = effectiveSort(query),
            hiddenByViewer = query.hiddenByViewer,
            // 활성 언어에서 해소되지 않는 장소가 붙은 글을 조회 조건으로 뺀다.
            language = language,
            first = clampPageSize(query.first),
            after = query.after,
            viewer = viewerContext(memberId),
        )
        // 거리순은 PostGIS 전용 포트로 간다.
        return when (search.sort) {
            PostSortOrder.LATEST -> communityQueryPort.findPosts(search)
            PostSortOrder.DISTANCE -> postSpatialQueryPort.findPostsByDistance(search)
        }
    }

    /** 좌표가 없으면 거리순 요청도 오류 없이 최신순으로 떨어진다. */
    private fun effectiveSort(query: PostsQuery): PostSortOrder = if (query.sort == PostSortOrder.DISTANCE && !query.hasCoordinates) {
        PostSortOrder.LATEST
    } else {
        query.sort
    }

    /** 좌표 반경. 좌표가 없으면 null이고 반경으로 좁히지 않는다. 반경은 글 자신의 좌표에 건다. */
    private fun resolveArea(query: PostsQuery): PostsArea? {
        if (!query.hasCoordinates) return null
        return PostsArea(
            longitude = requireNotNull(query.longitude),
            latitude = requireNotNull(query.latitude),
            // 상한 초과는 오류가 아니라 절삭이다.
            radiusMeters = query.radiusMeters.coerceIn(1, policy.maxRadiusMeters),
        )
    }

    /**
     * 글 단건. 조회수는 여기서만 오르며(피드 목록에서는 오르지 않음) 이벤트로 비동기 증가한다.
     * - 차단·숨김으로 못 본 글은 세지 않는다.
     */
    override fun post(postId: Long, memberId: Long): PostView? {
        val view = communityQueryPort.findPost(postId, viewerContext(memberId)) ?: return null
        eventPublisher.publish(PostViewed(postId))
        return view
    }

    /**
     * 글의 댓글 목록. 부모 글을 먼저 읽는다 — 댓글은 자기 `deleted_at`만 보므로 빼면 지운 글의 댓글이 읽힌다.
     * - 못 보는 글(삭제·차단·숨김)이면 오류가 아니라 빈 페이지다.
     */
    override fun comments(query: PostCommentsQuery, memberId: Long): CursorPage<CommentView> {
        val viewer = viewerContext(memberId)
        communityQueryPort.findPost(query.postId, viewer) ?: return emptyCommentPage()
        return communityQueryPort.findComments(
            PostCommentsSearch(
                postId = query.postId,
                first = clampPageSize(query.first),
                after = query.after,
                viewer = viewer,
            ),
        )
    }

    /** 못 보는 글의 댓글. 첫 페이지이므로 양쪽 다 false다. */
    private fun emptyCommentPage(): CursorPage<CommentView> = cursorPageOf(edges = emptyList(), hasNextPage = false, hasPreviousPage = false)

    override fun commentPreviews(postIds: List<Long>, memberId: Long): Map<Long, List<CommentView>> = communityQueryPort.findCommentPreviews(
        postIds = postIds,
        limitPerPost = policy.commentPreviewSize,
        viewer = viewerContext(memberId),
    )

    override fun commentReplies(
        parentCommentIds: List<Long>,
        memberId: Long,
    ): Map<Long, List<CommentView>> = communityQueryPort.findRepliesOf(parentCommentIds, viewerContext(memberId))

    override fun postTags(postIds: List<Long>): Map<Long, List<String>> = communityQueryPort.findTagsOf(postIds)

    override fun postPhotoKeys(postIds: List<Long>): Map<Long, List<String>> = communityQueryPort.findPhotoKeysOf(postIds)

    override fun postCategories(postIds: List<Long>): Map<Long, TargetCategory> = communityQueryPort.findCategoriesOf(postIds)

    override fun postCatalogIds(postIds: List<Long>): Map<Long, Long> = communityQueryPort.findCatalogIdsOf(postIds)

    override fun likedPostIds(postIds: List<Long>, memberId: Long): Set<Long> = communityQueryPort.findLikedPostIds(memberId, postIds)

    override fun blockedUsers(query: BlockedUsersQuery, memberId: Long): CursorPage<BlockedUserView> = communityQueryPort.findBlockedUsers(
        BlockedUsersSearch(
            memberId = memberId,
            first = clampPageSize(query.first),
            after = query.after,
        ),
    )

    /** 양방향 차단 판정(POL-041). */
    override fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean = counterpartId in communityQueryPort.findBlockedMemberIds(memberId)

    /** 내가 쓴 글. [viewerContext]를 쓰지 않는다 — 자기가 숨긴 자기 글도 보인다. */
    override fun myPosts(query: MyPostsQuery, memberId: Long): CursorPage<PostView> = communityQueryPort.findMyPosts(
        MyPostsSearch(
            memberId = memberId,
            first = clampPageSize(query.first),
            after = query.after,
        ),
    )

    /** 칩 구성은 tourism의 `app_category`가 소유한다(SPEC-0020). */
    override fun communityChips(appKey: AppKey, language: ContentLanguage): List<CommunityChipView> = categoryConfigPort.findCategories(appKey, language)

    /** 호출자의 양방향 차단 회원과 숨긴 글 id. 조회 조건으로 들어간다. */
    private fun viewerContext(memberId: Long): ViewerContext = ViewerContext(
        memberId = memberId,
        blockedMemberIds = communityQueryPort.findBlockedMemberIds(memberId),
        hiddenPostIds = communityQueryPort.findHiddenPostIds(memberId),
    )

    private fun clampPageSize(first: Int): Int = first.coerceIn(1, policy.maxPageSize)
}
