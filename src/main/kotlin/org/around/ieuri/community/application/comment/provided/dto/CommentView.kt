package org.around.ieuri.community.application.comment.provided.dto

import org.around.ieuri.community.application.shared.required.dto.MemberSummaryView
import java.time.Instant

data class CommentView(
    val id: Long,
    /** 최상위 댓글이면 null. 있으면 그 댓글에 달린 대댓글이다. */
    val parentCommentId: Long?,
    val body: String,
    val author: MemberSummaryView,
    val ownedByViewer: Boolean,
    val hidden: Boolean,
    val createdAt: Instant,
)
