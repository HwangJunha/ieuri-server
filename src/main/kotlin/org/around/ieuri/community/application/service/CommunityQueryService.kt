package org.around.ieuri.community.application.service

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.event.PostViewed
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.`in`.query.CommunityQueryUseCase
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.port.out.query.BlockedUsersSearch
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.PostCommentsSearch
import org.around.ieuri.community.application.port.out.query.PostSpatialQueryPort
import org.around.ieuri.community.application.port.out.query.PostsArea
import org.around.ieuri.community.application.port.out.query.PostsSearch
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.BlockedUserView
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.application.query.BlockedUsersQuery
import org.around.ieuri.community.application.query.CommentView
import org.around.ieuri.community.application.query.PostCommentsQuery
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.application.query.PostsQuery
import org.around.ieuri.community.domain.vo.AppKey
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.PostSortOrder
import org.around.ieuri.community.domain.vo.PostTag
import org.around.ieuri.community.domain.vo.TargetCategory
import org.springframework.stereotype.Service

/**
 * 커뮤니티 조회. **애그리게이트를 우회한다** — 읽기 전용이므로 QueryDSL 프로젝션이 read DTO를
 * 직접 만든다.
 *
 * 이 서비스의 핵심은 [viewerContext]다. 피드·단건·댓글 **세 경로가 모두 이것을 거친다** —
 * 이 스펙에서 가장 흔한 구현 실수 지점이며, 한 곳만 빠뜨려도 차단이 무력화된다. 특히 단건
 * 조회는 딥링크의 진입점이라 여기가 뚫리면 피드 필터가 아무리 정확해도 소용이 없다.
 *
 * 필터를 각 쿼리에 흩어 두지 않고 여기 모아 네 경로가 같은 구현을 공유하게 한다.
 */
@Service
class CommunityQueryService(
    private val communityQueryPort: CommunityQueryPort,
    private val postSpatialQueryPort: PostSpatialQueryPort,
    private val spotLookupPort: SpotLookupPort,
    private val eventPublisher: CommunityEventPublisherPort,
    private val policy: CommunityPolicy,
) : CommunityQueryUseCase {

    /**
     * 글 피드. **인자 조합이 범위를 정한다** — 장소 하나, 좌표 반경, 카테고리, 또는 전체다.
     * 정렬·페이징·모더레이션 필터는 모든 경우가 같다.
     */
    override fun posts(query: PostsQuery, memberId: Long): CursorPage<PostView> {
        val search = PostsSearch(
            spotId = query.spotId,
            area = resolveArea(query),
            category = query.category,
            // 태그는 정규화해 넘긴다 — 대소문자·선행 #을 가리지 않게 하려는 것이다.
            normalizedTag = query.tag?.let { PostTag.parseOrNull(it)?.normalized },
            sort = effectiveSort(query),
            first = clampPageSize(query.first),
            after = query.after,
            viewer = viewerContext(memberId),
        )
        // **정렬에 따라 포트가 갈린다** — 거리순은 PostGIS라 구현 기술이 다르다.
        return when (search.sort) {
            PostSortOrder.LATEST -> communityQueryPort.findPosts(search)
            PostSortOrder.DISTANCE -> postSpatialQueryPort.findPostsByDistance(search)
        }
    }

    /**
     * 좌표가 없으면 거리를 잴 기준이 없어 **거리순 요청도 최신순으로 떨어진다.**
     * 오류로 돌려주지 않는 이유는 클라이언트가 위치 권한을 못 받은 상태에서도 피드를
     * 그려야 하기 때문이다 — 정렬만 바뀌고 화면은 뜬다.
     */
    private fun effectiveSort(query: PostsQuery): PostSortOrder =
        if (query.sort == PostSortOrder.DISTANCE && !query.hasCoordinates) {
            PostSortOrder.LATEST
        } else {
            query.sort
        }

    /**
     * 좌표 반경. 좌표를 주지 않으면 null이고 반경으로 좁히지 않는다.
     *
     * **tourism에 spot 목록을 묻지 않는다.** 글이 자기 좌표를 들고 있어 반경 조건을 글
     * 조회에 직접 걸 수 있기 때문이며, 덕분에 목록 상한(2000)이 피드의 한계가 되던 문제가
     * 사라졌다 — 실측하면 기본 반경 20km 안에 spot이 12,000개가 넘는다.
     */
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
     * 글 단건. **여기서만 조회수가 오른다** — 피드 목록에서는 오르지 않는다.
     *
     * 증가는 이벤트로 넘겨 비동기로 처리하므로 응답이 UPDATE를 기다리지 않는다. 차단·숨김에
     * 걸려 못 본 글은 세지 않는다 — 보지 못한 것을 조회로 셀 수는 없다.
     */
    override fun post(postId: Long, memberId: Long): PostView? {
        val view = communityQueryPort.findPost(postId, viewerContext(memberId)) ?: return null
        eventPublisher.publish(PostViewed(postId))
        return view
    }

    override fun comments(query: PostCommentsQuery, memberId: Long): CursorPage<CommentView> =
        communityQueryPort.findComments(
            PostCommentsSearch(
                postId = query.postId,
                first = clampPageSize(query.first),
                after = query.after,
                viewer = viewerContext(memberId),
            ),
        )

    override fun commentPreviews(postIds: List<Long>, memberId: Long): Map<Long, List<CommentView>> =
        communityQueryPort.findCommentPreviews(
            postIds = postIds,
            limitPerPost = policy.commentPreviewSize,
            viewer = viewerContext(memberId),
        )

    override fun commentReplies(
        parentCommentIds: List<Long>,
        memberId: Long,
    ): Map<Long, List<CommentView>> =
        communityQueryPort.findRepliesOf(parentCommentIds, viewerContext(memberId))

    // 지연 필드 다섯. 포트로 그대로 넘긴다 — 여기서 더할 규칙이 없다.
    override fun postTags(postIds: List<Long>): Map<Long, List<String>> =
        communityQueryPort.findTagsOf(postIds)

    override fun postPhotoKeys(postIds: List<Long>): Map<Long, List<String>> =
        communityQueryPort.findPhotoKeysOf(postIds)

    override fun postCategories(postIds: List<Long>): Map<Long, TargetCategory> =
        communityQueryPort.findCategoriesOf(postIds)

    override fun postSpotIds(postIds: List<Long>): Map<Long, Long> =
        communityQueryPort.findSpotIdsOf(postIds)

    override fun likedPostIds(postIds: List<Long>, memberId: Long): Set<Long> =
        communityQueryPort.findLikedPostIds(memberId, postIds)

    override fun blockedUsers(query: BlockedUsersQuery, memberId: Long): CursorPage<BlockedUserView> =
        communityQueryPort.findBlockedUsers(
            BlockedUsersSearch(
                memberId = memberId,
                first = clampPageSize(query.first),
                after = query.after,
            ),
        )

    override fun communityChips(appKey: AppKey, language: ContentLanguage): List<CommunityChipView> =
        communityQueryPort.findChips(appKey, language)

    /** 호출자의 차단 관계를 **양방향으로** 읽어 담는다. */
    private fun viewerContext(memberId: Long): ViewerContext = ViewerContext(
        memberId = memberId,
        blockedMemberIds = communityQueryPort.findBlockedMemberIds(memberId),
    )

    private fun clampPageSize(first: Int): Int = first.coerceIn(1, policy.maxPageSize)
}
