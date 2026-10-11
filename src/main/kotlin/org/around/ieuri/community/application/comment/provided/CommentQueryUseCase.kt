package org.around.ieuri.community.application.comment.provided

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.comment.provided.dto.CommentView
import org.around.ieuri.community.application.comment.provided.dto.PostCommentsQuery

/** 댓글 조회. 글과 같은 모더레이션 필터를 거친다. */
interface CommentQueryUseCase {
    /** 글의 최상위 댓글. 못 보는 글(삭제·차단·숨김)이면 오류가 아니라 빈 페이지다. */
    fun comments(query: PostCommentsQuery, memberId: Long): CursorPage<CommentView>

    /** 여러 글의 댓글 미리보기를 한 번에. 전체 목록과 페이지네이션은 [comments]가 담당한다. */
    fun commentPreviews(postIds: List<Long>, memberId: Long): Map<Long, List<CommentView>>

    /** 여러 최상위 댓글의 대댓글. 오래된 순이며 페이지네이션이 없다. */
    fun commentReplies(parentCommentIds: List<Long>, memberId: Long): Map<Long, List<CommentView>>

    /** 지워지지 않은 댓글이 있는가. 차단·숨김은 보지 않는다. */
    fun exists(commentId: Long): Boolean
}
