package org.around.ieuri.community.application.port.out.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.query.BlockedUserView
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.application.query.CommentView
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.domain.vo.AppKey
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.domain.vo.PostSortOrder
import org.around.ieuri.community.domain.vo.PostTag

/**
 * 커뮤니티 읽기 아웃바운드 포트(CQRS 읽기). 애그리게이트를 우회해 필요한 값만 프로젝션한다.
 */
interface CommunityQueryPort {
    /**
     * 최신순 피드. **거리순은 [PostSpatialQueryPort]가 담당한다** — PostGIS 함수를 QueryDSL로
     * 표현할 수 없어 구현 기술이 갈리며, 포트를 나눠 그 사실을 계약에 드러낸다.
     */
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

    /**
     * 장소는 선택이라 **없는 글이 정상이다.** 그 글은 결과에 키가 없다.
     *
     * 돌려주는 값은 **카탈로그 id**다(SPEC-0019) — 칩을 그릴 때 요약 조회의 입력이 된다.
     * 글이 가리키는 것은 장소이지 특정 언어의 spot 행이 아니다.
     */
    fun findCatalogIdsOf(postIds: List<Long>): Map<Long, Long>

    /**
     * [memberId]가 좋아요한 글의 id만. 나머지는 좋아요하지 않았다는 뜻이다.
     * 차단은 이 판정에 영향을 주지 않아 ViewerContext를 받지 않는다 — 받으면 차단 관계를
     * 읽는 쿼리가 공짜로 한 번 더 붙는다.
     */
    fun findLikedPostIds(memberId: Long, postIds: List<Long>): Set<Long>

    /**
     * 갱신된 좋아요 수. 카운터를 원자적으로 올린 **뒤** 읽는다 — 쓰기가 애그리게이트를 거치지
     * 않으므로 갱신값이 커맨드의 반환으로 따라오지 않기 때문이다.
     *
     * 글이 없으면 null. 커맨드가 방금 갱신한 직후라 실제로는 항상 존재한다.
     */
    fun findLikeCount(postId: Long): Int?

    /**
     * 여러 최상위 댓글의 대댓글을 **한 번에**. 오래된 순이며 페이지네이션이 없다.
     * 대댓글이 없는 댓글은 결과에 키가 없다.
     */
    fun findRepliesOf(
        parentCommentIds: List<Long>,
        viewer: ViewerContext,
    ): Map<Long, List<CommentView>>

    fun findBlockedUsers(search: BlockedUsersSearch): CursorPage<BlockedUserView>

    /**
     * 본인이 작성한 글(SPEC-0011 R3). 작성 시각 내림차순.
     *
     * **모더레이션 필터를 거치지 않는다** — 자기 글은 자기가 숨겼더라도 마이페이지에 보여야
     * 하고, 자기 자신과는 차단 관계가 없다. 삭제된 글만 제외한다.
     */
    fun findMyPosts(search: MyPostsSearch): CursorPage<PostView>

    /**
     * [memberId]와 차단 관계에 있는 상대의 id. **양방향을 한 번의 쿼리로** 읽는다 —
     * 내가 차단한 쪽과 나를 차단한 쪽을 OR로 묶고 상대 id만 프로젝션한다.
     */
    fun findBlockedMemberIds(memberId: Long): Set<Long>

    /** [memberId]가 개별로 숨긴 글의 id. 피드 필터가 이 집합을 제외한다. */
    fun findHiddenPostIds(memberId: Long): Set<Long>

    /** 앱의 켜진 칩만 순서대로. 구성이 없으면 빈 목록이다. */
    fun findChips(appKey: AppKey, language: ContentLanguage): List<CommunityChipView>
}

/**
 * 거리순 피드 전용 읽기 포트.
 *
 * **별도 포트인 이유는 구현 기술이 다르기 때문이다.** 반경 조건과 거리 정렬은 PostGIS
 * 함수인데 QueryDSL로 표현되지 않고, `location`(geography)은 대응하는 Kotlin 타입이 없어
 * 엔티티에 매핑조차 할 수 없다 — 매핑하면 ddl-auto=validate가 기동을 막는다.
 * map_x/map_y로 매번 점을 만들어 우회하면 GiST 인덱스를 타지 못한다
 * (실측: Seq Scan 389ms 대 Index Scan 0.3ms).
 *
 * 포트를 나눠 두면 application이 어댑터끼리 몰래 엮이는 대신 이 계약만 보고 쓴다.
 */
interface PostSpatialQueryPort {
    /**
     * 반경 안의 글을 **가까운 순으로** 한 페이지. 같은 거리면 최신순이다.
     *
     * 모더레이션·카테고리·태그 필터를 **이 조회가 함께 건다** — 일부만 걸고 나머지를 뒤에서
     * 거르면 요청한 개수를 채우려고 넉넉히 읽어야 하고, 그때 몇 배를 읽을지가 근거 없는
     * 상수가 된다. 한 번에 거르면 요청 개수가 곧 정확한 페이지 크기다.
     */
    fun findPostsByDistance(search: PostsSearch): CursorPage<PostView>
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
    /**
     * 호출자가 **개별로 숨긴** 글의 id. 차단이 사람 단위라면 이쪽은 글 단위다.
     *
     * 조회 시점에 한 번 읽어 담는 이유는 차단과 같다 — 필터가 쿼리 조건으로 들어가야
     * `first: 20`을 요청했을 때 20건이 온다.
     */
    val hiddenPostIds: Set<Long> = emptySet(),
)

/**
 * 글 피드 조회 조건.
 *
 * **좌표 반경을 그대로 넘긴다.** 이전에는 tourism에 반경 안 spot id 목록을 물어 `IN` 절에
 * 넣었는데, 그 목록의 상한(2000)이 곧 피드의 한계가 됐다 — 실측하면 기본 반경 20km 안에
 * spot이 12,000개가 넘어 대부분이 조용히 잘렸다. 글이 자기 좌표를 들고 있으므로 반경 조건을
 * 글 조회에 직접 건다.
 */
data class PostsSearch(
    /**
     * 이 장소에 붙은 글만. null이면 장소 하나로 좁히지 않는다.
     *
     * **`spot.id`가 아니라 카탈로그 id다**(SPEC-0019 R-12) — 그래야 언어가 달라도 같은
     * 장소의 글이 한 피드에 모인다.
     */
    val catalogId: Long?,
    /** 좌표 반경. null이면 반경으로 좁히지 않는다. */
    val area: PostsArea?,
    /** 상단 칩. null이면 카테고리로 좁히지 않는다. */
    val category: TargetCategory?,
    /** 정규화된 태그. null이면 태그로 좁히지 않는다. */
    val normalizedTag: String?,
    /**
     * 정렬 방식. **무엇이 보이는지까지 정한다** —
     * LATEST는 [area]로 좁힌 경우에도 장소 없는 글을 함께 담고,
     * DISTANCE는 반경 안 장소에 붙은 글만 담는다(거리 없는 글은 자리가 없다).
     */
    val sort: PostSortOrder,
    /**
     * true면 **숨긴 글만** 돌려준다(해제 화면). 이때는 숨김 필터를 우회해야 한다 —
     * 그러지 않으면 항상 빈 목록이라 사용자가 숨김을 되돌릴 방법이 없다.
     */
    val hiddenByViewer: Boolean,
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
