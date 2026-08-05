package org.around.ieuri.community.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.query.BlockedUserView
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.application.query.BlockedUsersQuery
import org.around.ieuri.community.application.query.CommentView
import org.around.ieuri.community.application.query.PostCommentsQuery
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.application.query.PostsQuery
import org.around.ieuri.community.domain.vo.AppKey
import org.around.ieuri.community.domain.vo.ContentLanguage

/**
 * 커뮤니티 조회 유스케이스(CQRS 읽기). 도메인적으로 모두 커뮤니티 콘텐츠 조회이므로 하나로 묶는다.
 *
 * 모든 조회가 인증을 전제한다 — 이 앱은 로그인 없이 들어올 수 없다.
 * 세 조회 경로(피드·단건·댓글)가 **같은 모더레이션 필터**를 거친다.
 */
interface CommunityQueryUseCase {
    /** 글 피드. 범위는 [PostsQuery]의 인자 조합이 정한다. */
    fun posts(query: PostsQuery, memberId: Long): CursorPage<PostView>

    /** 차단·숨김에 걸리면 오류가 아니라 null이다. 작성자 본인만 예외다. */
    fun post(postId: Long, memberId: Long): PostView?

    fun comments(query: PostCommentsQuery, memberId: Long): CursorPage<CommentView>

    /**
     * 여러 글의 댓글 미리보기. 피드 카드에 몇 건씩 얹을 때 쓰며 **한 번에 읽는다**.
     * 전체 목록과 페이지네이션은 [comments]가 담당한다.
     */
    fun commentPreviews(postIds: List<Long>, memberId: Long): Map<Long, List<CommentView>>

    fun blockedUsers(query: BlockedUsersQuery, memberId: Long): CursorPage<BlockedUserView>

    /**
     * 상단 카테고리 칩. 앱마다·언어마다 구성이 다르므로 둘로 고른다 —
     * 카테고리의 가용 집합이 언어별로 갈리기 때문이다(여행코스는 국문 전용, 교통은 외국어 전용).
     * 구성이 없는 앱이면 빈 목록이며 오류가 아니다 — 앱은 칩 영역이 비는 경우를 허용해야 한다.
     */
    fun communityChips(appKey: AppKey, language: ContentLanguage): List<CommunityChipView>
}
