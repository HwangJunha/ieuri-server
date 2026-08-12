package org.around.ieuri.community.application.service

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.`in`.query.FeedQueryUseCase
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.FeedQueryPort
import org.around.ieuri.community.application.port.out.query.FeedSearch
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.FeedItemView
import org.springframework.stereotype.Service

/**
 * 피드 조회(SPEC-0017 R4·R11). 애그리게이트를 우회해 QueryDSL 프로젝션이 read DTO를 만든다.
 *
 * **정렬 인자를 받지 않는다**(POL-019) — 최신순 고정이다. 정렬을 받으면 커서 인코딩이 정렬
 * 키에 종속되어, 나중에 정렬을 추가할 때 기존 커서가 깨진다.
 *
 * 차단 필터는 [viewerContext]로 질의에 실려 들어간다(AC11-1·11-2) — 조회 후 걸러내면 페이지
 * 크기가 들쭉날쭉해지고 hasNextPage가 거짓말을 한다.
 */
@Service
class FeedQueryService(
    private val feedQueryPort: FeedQueryPort,
    private val communityQueryPort: CommunityQueryPort,
    private val policy: CommunityPolicy,
) : FeedQueryUseCase {

    override fun feed(first: Int?, after: String?, memberId: Long): CursorPage<FeedItemView> =
        feedQueryPort.findFeed(
            FeedSearch(
                first = clampPageSize(first),
                after = after,
                viewer = viewerContext(memberId),
            ),
        )

    /** 차단·삭제·숨김에 걸리면 오류가 아니라 null이다 — 기존 `post(id:)`와 같은 관례다. */
    override fun feedItem(feedItemId: Long, memberId: Long): FeedItemView? =
        feedQueryPort.findFeedItem(feedItemId, viewerContext(memberId))

    // 지연 필드 둘. 포트로 그대로 넘긴다 — 여기서 더할 규칙이 없다.
    override fun feedItemPhotoKeys(feedItemIds: List<Long>): Map<Long, List<String>> =
        feedQueryPort.findPhotoKeysOf(feedItemIds)

    override fun likedFeedItemIds(feedItemIds: List<Long>, memberId: Long): Set<Long> =
        feedQueryPort.findLikedFeedItemIds(memberId, feedItemIds)

    /**
     * 호출자의 차단 관계를 **양방향으로** 읽어 담는다.
     *
     * 글과 달리 hiddenPostIds를 담지 않는다 — 피드에는 개별 숨김 기능이 없다(비범위).
     */
    private fun viewerContext(memberId: Long): ViewerContext = ViewerContext(
        memberId = memberId,
        blockedMemberIds = communityQueryPort.findBlockedMemberIds(memberId),
    )

    /** 상한 초과는 오류가 아니라 절삭이다(AC4-3, POL-018). null이면 계약의 기본값이다. */
    private fun clampPageSize(first: Int?): Int =
        (first ?: DEFAULT_FEED_PAGE_SIZE).coerceIn(1, policy.maxPageSize)

    private companion object {
        const val DEFAULT_FEED_PAGE_SIZE = 20
    }
}
