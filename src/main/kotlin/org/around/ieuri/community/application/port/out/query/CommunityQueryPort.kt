package org.around.ieuri.community.application.port.out.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.query.BlockedUserView
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.application.query.CommentView
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.domain.vo.AppKey
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.domain.vo.PostTag

/**
 * 커뮤니티 읽기 아웃바운드 포트(CQRS 읽기). 애그리게이트를 우회해 필요한 값만 프로젝션한다.
 */
interface CommunityQueryPort {
    fun findPosts(search: PostsSearch): CursorPage<PostView>

    /** 글 단건. 피드와 같은 모더레이션 필터를 거치며, 걸러지면 null이다. */
    fun findPost(postId: Long, viewer: ViewerContext): PostView?

    fun findComments(search: PostCommentsSearch): CursorPage<CommentView>

    /**
     * 여러 글의 댓글 미리보기를 **한 번에** 읽는다. 글마다 물으면 피드 한 페이지에 쿼리가
     * 20번 붙는다(N+1) — DataLoader가 글 id를 모아 이 메서드를 한 번 부른다.
     *
     * 글마다 최신 [limitPerPost]건만 담는다. 댓글이 없는 글은 결과에 키가 없다.
     */
    fun findCommentPreviews(
        postIds: List<Long>,
        limitPerPost: Int,
        viewer: ViewerContext,
    ): Map<Long, List<CommentView>>

    /**
     * 아래 넷은 전부 **글 여러 건을 한 번에** 읽는다 — 각각 DataLoader가 하나씩 물고 있어,
     * 클라이언트가 그 필드를 고른 경우에만, 고른 글 전부를 한 번의 쿼리로 가져온다.
     * 결과에 키가 없으면 그 글에는 값이 없다는 뜻이다.
     */
    fun findTagsOf(postIds: List<Long>): Map<Long, List<String>>

    /** 첨부 순서대로. 사진이 없는 글은 결과에 키가 없다. */
    fun findPhotoKeysOf(postIds: List<Long>): Map<Long, List<String>>

    /** 카테고리는 모든 글에 있다 — 키가 없으면 연결이 깨진 것이다. */
    fun findCategoriesOf(postIds: List<Long>): Map<Long, TargetCategory>

    /** 장소는 선택이라 **없는 글이 정상이다.** 그 글은 결과에 키가 없다. */
    fun findSpotIdsOf(postIds: List<Long>): Map<Long, Long>

    /**
     * [memberId]가 좋아요한 글의 id만. 나머지는 좋아요하지 않았다는 뜻이다.
     * 차단은 이 판정에 영향을 주지 않아 ViewerContext를 받지 않는다 — 받으면 차단 관계를
     * 읽는 쿼리가 공짜로 한 번 더 붙는다.
     */
    fun findLikedPostIds(memberId: Long, postIds: List<Long>): Set<Long>

    fun findBlockedUsers(search: BlockedUsersSearch): CursorPage<BlockedUserView>

    /**
     * [memberId]와 차단 관계에 있는 상대의 id. **양방향을 한 번의 쿼리로** 읽는다 —
     * 내가 차단한 쪽과 나를 차단한 쪽을 OR로 묶고 상대 id만 프로젝션한다.
     */
    fun findBlockedMemberIds(memberId: Long): Set<Long>

    /** 앱의 켜진 칩만 순서대로. 구성이 없으면 빈 목록이다. */
    fun findChips(appKey: AppKey, language: ContentLanguage): List<CommunityChipView>
}

/**
 * 조회 호출자와 그 차단 관계. **세 조회 경로가 모두 이 값을 거친다** — 피드·단건·댓글이다.
 *
 * 차단 관계를 조회 시점에 한 번 읽어 담아 두는 이유는 필터가 쿼리 조건으로 들어가야 하기
 * 때문이다. 조회 후 걸러내면 `first: 20`을 요청했는데 15건만 남는다.
 *
 * [blockedMemberIds]는 **양방향으로 해석된 결과**다 — "내가 차단한" 과 "나를 차단한"을
 * 합집합으로 담는다.
 */
data class ViewerContext(
    val memberId: Long,
    val blockedMemberIds: Set<Long>,
)

/**
 * 글 피드 조회 조건. **장소 해소가 끝난 상태로 온다** — 좌표 반경 판정은 spot을 소유한
 * tourism의 몫이고, 이 포트는 글만 다룬다.
 *
 * [spotIds]가 null이면 장소로 좁히지 않는다(전체 피드). 빈 목록이면 결과도 비어 있다 —
 * 반경 안에 장소가 하나도 없는 경우이며, 구현은 빈 `IN` 절을 만들지 않도록 조기 반환해야 한다.
 */
data class PostsSearch(
    val spotIds: List<Long>?,
    /** 상단 칩. null이면 카테고리로 좁히지 않는다. */
    val category: TargetCategory?,
    /** 정규화된 태그. null이면 태그로 좁히지 않는다. */
    val normalizedTag: String?,
    val first: Int,
    val after: String?,
    val viewer: ViewerContext,
)

data class PostCommentsSearch(
    val postId: Long,
    val first: Int,
    val after: String?,
    val viewer: ViewerContext,
)

data class BlockedUsersSearch(
    val memberId: Long,
    val first: Int,
    val after: String?,
)
