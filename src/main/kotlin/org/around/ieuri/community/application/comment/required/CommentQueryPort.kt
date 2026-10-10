package org.around.ieuri.community.application.comment.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.comment.provided.dto.CommentView
import org.around.ieuri.community.application.comment.required.dto.PostCommentsSearch
import org.around.ieuri.community.application.shared.required.dto.ViewerContext

/** 댓글 읽기 포트(CQRS 읽기). 차단·숨김 필터를 글 조회와 같은 [ViewerContext]로 건다. */
interface CommentQueryPort {
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

    /**
     * 여러 최상위 댓글의 대댓글을 한 번에. 오래된 순이며 페이지네이션이 없다.
     * 대댓글이 없는 댓글은 결과에 키가 없다.
     */
    fun findRepliesOf(
        parentCommentIds: List<Long>,
        viewer: ViewerContext,
    ): Map<Long, List<CommentView>>
}
