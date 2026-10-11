package org.around.ieuri.community.application.comment

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.around.ieuri.community.application.comment.provided.CommentQueryUseCase
import org.around.ieuri.community.application.comment.provided.dto.CommentView
import org.around.ieuri.community.application.comment.provided.dto.PostCommentsQuery
import org.around.ieuri.community.application.comment.required.CommentQueryPort
import org.around.ieuri.community.application.comment.required.dto.PostCommentsSearch
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.around.ieuri.community.application.shared.PagePolicy
import org.around.ieuri.community.application.shared.required.dto.ViewerContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 댓글 조회. 조회자의 차단·숨김을 조건으로 건다. */
@Service
@Transactional(readOnly = true)
class CommentQueryService(
    private val commentQueryPort: CommentQueryPort,
    private val postQueryUseCase: PostQueryUseCase,
    private val blockQueryUseCase: BlockQueryUseCase,
    private val pagePolicy: PagePolicy,
    private val policy: CommentPolicy,
) : CommentQueryUseCase {

    /** 부모 글을 먼저 읽는다 — 댓글은 자기 `deleted_at`만 보므로 빼면 지운 글의 댓글이 읽힌다. */
    override fun comments(query: PostCommentsQuery, memberId: Long): CursorPage<CommentView> {
        // 못 보는 글의 댓글은 첫 페이지이므로 양쪽 다 false다.
        if (!postQueryUseCase.isVisible(query.postId, memberId)) return cursorPageOf(edges = emptyList(), hasNextPage = false, hasPreviousPage = false)
        return commentQueryPort.findComments(
            PostCommentsSearch(postId = query.postId, first = pagePolicy.clamp(query.first), after = query.after, viewer = viewerContext(memberId)),
        )
    }

    override fun commentPreviews(postIds: List<Long>, memberId: Long): Map<Long, List<CommentView>> = commentQueryPort.findCommentPreviews(postIds = postIds, limitPerPost = policy.previewSize, viewer = viewerContext(memberId))

    override fun commentReplies(parentCommentIds: List<Long>, memberId: Long): Map<Long, List<CommentView>> = commentQueryPort.findRepliesOf(parentCommentIds, viewerContext(memberId))

    /** 조회자의 양방향 차단 회원. 개인 숨김(hiddenPostIds)은 글 전용이라 읽지 않는다. */
    private fun viewerContext(memberId: Long): ViewerContext = ViewerContext(memberId = memberId, blockedMemberIds = blockQueryUseCase.blockedMemberIds(memberId))
}
