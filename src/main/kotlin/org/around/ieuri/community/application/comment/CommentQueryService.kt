package org.around.ieuri.community.application.comment

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.comment.provided.CommentQueryUseCase
import org.around.ieuri.community.application.shared.CommunityPolicy
import org.around.ieuri.community.application.shared.clampPageSize
import org.around.ieuri.community.application.shared.dto.CommentView
import org.around.ieuri.community.application.shared.dto.PostCommentsQuery
import org.around.ieuri.community.application.shared.required.CommunityQueryPort
import org.around.ieuri.community.application.shared.required.PostCommentsSearch
import org.around.ieuri.community.application.shared.viewerContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 댓글 조회. 조회자의 차단·숨김을 조건으로 건다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class CommentQueryService(
    private val communityQueryPort: CommunityQueryPort,
    private val policy: CommunityPolicy,
) : CommentQueryUseCase {

    /** 부모 글을 먼저 읽는다 — 댓글은 자기 `deleted_at`만 보므로 빼면 지운 글의 댓글이 읽힌다. */
    override fun comments(query: PostCommentsQuery, memberId: Long): CursorPage<CommentView> {
        val viewer = communityQueryPort.viewerContext(memberId)
        // 못 보는 글의 댓글은 첫 페이지이므로 양쪽 다 false다.
        communityQueryPort.findPost(query.postId, viewer) ?: return cursorPageOf(edges = emptyList(), hasNextPage = false, hasPreviousPage = false)
        return communityQueryPort.findComments(
            PostCommentsSearch(postId = query.postId, first = policy.clampPageSize(query.first), after = query.after, viewer = viewer),
        )
    }

    override fun commentPreviews(postIds: List<Long>, memberId: Long): Map<Long, List<CommentView>> = communityQueryPort.findCommentPreviews(postIds = postIds, limitPerPost = policy.commentPreviewSize, viewer = communityQueryPort.viewerContext(memberId))

    override fun commentReplies(parentCommentIds: List<Long>, memberId: Long): Map<Long, List<CommentView>> = communityQueryPort.findRepliesOf(parentCommentIds, communityQueryPort.viewerContext(memberId))
}
