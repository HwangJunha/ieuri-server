package org.around.ieuri.community.application.shared.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.shared.dto.BlockedUserView
import org.around.ieuri.community.application.shared.dto.CommentView
import org.around.ieuri.community.application.shared.dto.PostView
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.around.ieuri.community.domain.shared.PostSortOrder
import org.around.ieuri.community.domain.shared.TargetCategory

/**
 * 커뮤니티 읽기 아웃바운드 포트(CQRS 읽기). 애그리게이트를 우회해 필요한 값만 프로젝션한다.
 */
interface CommunityQueryPort {
    /** 최신순 피드. 거리순은 [PostSpatialQueryPort]가 담당한다. */
    fun findPosts(search: PostsSearch): CursorPage<PostView>

    /** 글 단건. 피드와 같은 모더레이션 필터를 거치며, 걸러지면 null이다. */
    fun findPost(postId: Long, viewer: ViewerContext): PostView?

    fun findComments(search: PostCommentsSearch): CursorPage<CommentView>

    /**
     * 여러 글의 댓글 미리보기를 한 번에 읽는다(DataLoader용).
     * - 글마다 최신 [limitPerPost]건만 담는다. 댓글이 없는 글은 결과에 키가 없다.
     */
    fun findCommentPreviews(
        postIds: List<Long>,
        limitPerPost: Int,
        viewer: ViewerContext,
    ): Map<Long, List<CommentView>>

    /** 글 여러 건의 태그를 한 번에(DataLoader용). 결과에 키가 없으면 그 글에는 값이 없다. */
    fun findTagsOf(postIds: List<Long>): Map<Long, List<String>>

    /** 첨부 순서대로. 사진이 없는 글은 결과에 키가 없다. */
    fun findPhotoKeysOf(postIds: List<Long>): Map<Long, List<String>>

    /** 카테고리는 모든 글에 있다 — 키가 없으면 연결이 깨진 것이다. */
    fun findCategoriesOf(postIds: List<Long>): Map<Long, TargetCategory>

    /** 글이 가리키는 카탈로그 id(SPEC-0019). 장소 없는 글은 정상이며 결과에 키가 없다. */
    fun findCatalogIdsOf(postIds: List<Long>): Map<Long, Long>

    /** [memberId]가 좋아요한 글의 id만. 차단은 이 판정에 영향을 주지 않는다. */
    fun findLikedPostIds(memberId: Long, postIds: List<Long>): Set<Long>

    /**
     * 여러 최상위 댓글의 대댓글을 한 번에. 오래된 순이며 페이지네이션이 없다.
     * 대댓글이 없는 댓글은 결과에 키가 없다.
     */
    fun findRepliesOf(
        parentCommentIds: List<Long>,
        viewer: ViewerContext,
    ): Map<Long, List<CommentView>>

    fun findBlockedUsers(search: BlockedUsersSearch): CursorPage<BlockedUserView>

    /**
     * 본인이 작성한 글(SPEC-0011 R3). 작성 시각 내림차순.
     * - 모더레이션 필터를 거치지 않는다(자기가 숨긴 글도 나온다). 삭제된 글만 제외한다.
     */
    fun findMyPosts(search: MyPostsSearch): CursorPage<PostView>

    /** [memberId]와 차단 관계에 있는 상대의 id. 내가 차단한 쪽과 나를 차단한 쪽을 모두 담는다. */
    fun findBlockedMemberIds(memberId: Long): Set<Long>

    /** [memberId]가 개별로 숨긴 글의 id. 피드 필터가 이 집합을 제외한다. */
    fun findHiddenPostIds(memberId: Long): Set<Long>
}

/** 거리순 피드 전용 읽기 포트. PostGIS 함수가 필요해 raw SQL 어댑터가 구현한다. */
interface PostSpatialQueryPort {
    /**
     * 반경 안의 글을 가까운 순으로 한 페이지. 같은 거리면 최신순이다.
     * - 모더레이션·카테고리·태그 필터를 이 조회가 함께 걸어, 요청 개수가 곧 페이지 크기다.
     */
    fun findPostsByDistance(search: PostsSearch): CursorPage<PostView>
}

/**
 * 조회 호출자와 그 차단 관계. 피드·단건·댓글 조회가 모두 이 값을 쿼리 조건으로 건다.
 * - [blockedMemberIds]는 "내가 차단한"과 "나를 차단한"의 합집합이다.
 */
data class ViewerContext(
    val memberId: Long,
    val blockedMemberIds: Set<Long>,
    /** 호출자가 개별로 숨긴 글의 id. */
    val hiddenPostIds: Set<Long> = emptySet(),
)

/** 글 피드 조회 조건. 반경은 좌표로 넘겨 글 조회에 직접 건다. */
data class PostsSearch(
    /** 이 장소에 붙은 글만. `spot.id`가 아니라 카탈로그 id다(SPEC-0019). null이면 좁히지 않는다. */
    val catalogId: Long?,
    /** 좌표 반경. null이면 반경으로 좁히지 않는다. */
    val area: PostsArea?,
    /** 카테고리 선택. 비어 있으면 좁히지 않고, 값이 여럿이면 IN으로 거른다(OR). */
    val categories: Set<TargetCategory>,
    /** 정규화된 태그. null이면 태그로 좁히지 않는다. */
    val normalizedTag: String?,
    /**
     * 정렬 방식이자 노출 범위.
     * - LATEST는 [area]로 좁혀도 장소 없는 글을 함께 담는다.
     * - DISTANCE는 반경 안 장소에 붙은 글만 담는다.
     */
    val sort: PostSortOrder,
    /** true면 숨긴 글만 돌려준다(해제 화면). 이때는 숨김 필터를 우회한다. */
    val hiddenByViewer: Boolean,
    /**
     * 요청의 활성 언어(SPEC-0029).
     * - 이 언어에서 해소되지 않는 장소가 붙은 글은 오류 없이 목록에서 빠진다.
     * - 장소가 붙지 않은 글은 언어와 무관하게 남는다.
     */
    val language: ContentLanguage,
    val first: Int,
    val after: String?,
    val viewer: ViewerContext,
)

/** 반경 조회의 중심과 거리. 상한 절삭은 애플리케이션이 이미 마친 상태로 온다. */
data class PostsArea(
    val longitude: Double,
    val latitude: Double,
    val radiusMeters: Int,
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

/** 내가 쓴 글 조회 조건(SPEC-0011 R3). 위치·카테고리로 좁히지 않는다. */
data class MyPostsSearch(
    val memberId: Long,
    val first: Int,
    val after: String?,
)
