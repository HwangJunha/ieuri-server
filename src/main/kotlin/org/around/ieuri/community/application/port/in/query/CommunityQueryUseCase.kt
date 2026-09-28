package org.around.ieuri.community.application.port.`in`.query

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.query.BlockedUserView
import org.around.ieuri.community.application.query.BlockedUsersQuery
import org.around.ieuri.community.application.query.CommentView
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.application.query.MyPostsQuery
import org.around.ieuri.community.application.query.PostCommentsQuery
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.application.query.PostsQuery
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.TargetCategory

/**
 * 커뮤니티 조회 유스케이스(CQRS 읽기). 모든 조회가 인증을 전제한다.
 * - 피드·단건·댓글이 같은 모더레이션 필터를 거친다.
 */
interface CommunityQueryUseCase {
    /**
     * 글 피드. 범위는 [PostsQuery]의 인자 조합이 정한다.
     * - [language]에서 해소되지 않는 장소가 붙은 글은 목록에서 빠진다(SPEC-0029).
     * - 장소가 붙지 않은 글은 언어와 무관하게 남는다.
     */
    fun posts(query: PostsQuery, memberId: Long, language: ContentLanguage): CursorPage<PostView>

    /** 차단·숨김에 걸리면 오류가 아니라 null이다. 작성자 본인만 예외다. */
    fun post(postId: Long, memberId: Long): PostView?

    fun comments(query: PostCommentsQuery, memberId: Long): CursorPage<CommentView>

    /** 여러 글의 댓글 미리보기를 한 번에. 전체 목록과 페이지네이션은 [comments]가 담당한다. */
    fun commentPreviews(postIds: List<Long>, memberId: Long): Map<Long, List<CommentView>>

    /** 글의 지연 필드(DataLoader용). 결과에 키가 없으면 그 글에 값이 없다는 뜻이다. */
    fun postTags(postIds: List<Long>): Map<Long, List<String>>

    fun postPhotoKeys(postIds: List<Long>): Map<Long, List<String>>

    fun postCategories(postIds: List<Long>): Map<Long, TargetCategory>

    /** 장소는 선택이라 없는 글이 정상이다. 값은 카탈로그 id다(SPEC-0019). */
    fun postCatalogIds(postIds: List<Long>): Map<Long, Long>

    /** 호출자가 좋아요한 글의 id만. */
    fun likedPostIds(postIds: List<Long>, memberId: Long): Set<Long>

    /** 여러 최상위 댓글의 대댓글. 오래된 순이며 페이지네이션이 없다. */
    fun commentReplies(parentCommentIds: List<Long>, memberId: Long): Map<Long, List<CommentView>>

    fun blockedUsers(query: BlockedUsersQuery, memberId: Long): CursorPage<BlockedUserView>

    /** 두 회원이 어느 방향으로든 차단 관계인지(POL-041). 알림 발송 판정용(SPEC-0028). */
    fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean

    /** 본인이 작성한 글(SPEC-0011 R3). 작성 시각 내림차순이며 숨김·차단 필터를 거치지 않는다. */
    fun myPosts(query: MyPostsQuery, memberId: Long): CursorPage<PostView>

    /** 앱·언어별 상단 카테고리 칩. 구성이 없는 앱이면 오류가 아니라 빈 목록이다. */
    fun communityChips(appKey: AppKey, language: ContentLanguage): List<CommunityChipView>
}
