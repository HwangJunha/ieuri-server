package org.around.ieuri.community.application.service

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.`in`.query.CommunityQueryUseCase
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.query.BlockedUsersSearch
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.PostCommentsSearch
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
import org.around.ieuri.community.domain.vo.PostTag
import org.around.ieuri.community.domain.vo.PostTarget
import org.around.ieuri.community.domain.vo.PostTargetType
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
    private val spotLookupPort: SpotLookupPort,
    private val policy: CommunityPolicy,
) : CommunityQueryUseCase {

    /**
     * 글 피드. **인자 조합이 범위를 정한다** — 대상 하나, 좌표 반경, 또는 전체다.
     * 정렬·페이징·모더레이션 필터는 세 경우가 모두 같다.
     */
    override fun posts(query: PostsQuery, memberId: Long): CursorPage<PostView> =
        communityQueryPort.findPosts(
            PostsSearch(
                targets = resolveTargets(query),
                // 태그는 정규화해 넘긴다 — 대소문자·선행 #을 가리지 않게 하려는 것이다.
                normalizedTag = query.tag?.let { PostTag.parseOrNull(it)?.normalized },
                first = clampPageSize(query.first),
                after = query.after,
                viewer = viewerContext(memberId),
            ),
        )

    /**
     * 어떤 대상의 글을 볼지 정한다. null이면 대상으로 좁히지 않는다(전체 피드).
     *
     * 좌표를 받으면 반경 안의 spot을 **tourism에 물어** 목록으로 바꾼다 — `spot` 테이블은
     * tourism의 것이라 커뮤니티가 직접 조회하지 않는다. 대상 수가 상한을 넘으면 잘리므로
     * 반경이 매우 넓으면 결과가 부분집합이 될 수 있다.
     */
    private fun resolveTargets(query: PostsQuery): List<PostTarget>? {
        if (query.target != null) {
            return listOf(query.target)
        }
        if (!query.hasCoordinates) {
            return null
        }

        val spotIds = spotLookupPort.findIdsWithin(
            latitude = requireNotNull(query.latitude),
            longitude = requireNotNull(query.longitude),
            // 상한 초과는 오류가 아니라 절삭이다.
            radiusMeters = query.radiusMeters.coerceIn(1, policy.maxRadiusMeters),
            limit = policy.maxNearbySpots,
        )
        return filterByCategory(spotIds, query.category)
    }

    /**
     * 상단 칩 필터. 카테고리는 spot이 들고 있으므로 요약을 한 번에 읽어 거른다.
     * 칩을 고르지 않았으면 조회 자체를 하지 않는다.
     */
    private fun filterByCategory(spotIds: List<Long>, category: TargetCategory?): List<PostTarget> {
        if (category == null) {
            return spotIds.map { PostTarget(PostTargetType.SPOT, it) }
        }

        val summaries = spotLookupPort.findSummaries(spotIds)
        val matched = mutableListOf<PostTarget>()
        for (spotId in spotIds) {
            if (summaries[spotId]?.category == category) {
                matched += PostTarget(PostTargetType.SPOT, spotId)
            }
        }
        return matched
    }

    override fun post(postId: Long, memberId: Long): PostView? =
        communityQueryPort.findPost(postId, viewerContext(memberId))

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
