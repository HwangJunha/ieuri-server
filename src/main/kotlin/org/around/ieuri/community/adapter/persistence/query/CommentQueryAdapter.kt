package org.around.ieuri.community.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.adapter.persistence.mapper.toCommentView
import org.around.ieuri.community.adapter.persistence.table.CommentTable
import org.around.ieuri.community.application.comment.provided.dto.CommentView
import org.around.ieuri.community.application.comment.required.CommentQueryPort
import org.around.ieuri.community.application.comment.required.dto.PostCommentsSearch
import org.around.ieuri.community.application.shared.required.dto.ViewerContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/**
 * 댓글 읽기 어댑터. 트랜잭션은 호출하는 조회 서비스가 연다.
 * - 작성자 닉네임 등 다른 도메인 소유 정보는 비워 두고 리졸버가 채운다.
 */
@Component
class CommentQueryAdapter : CommentQueryPort {

    /** 최상위 댓글만. 대댓글은 Comment.replies가 따로 내려준다. */
    override fun findComments(search: PostCommentsSearch): CursorPage<CommentView> {
        val cursor = search.after?.let(::decodeCursor)
        val viewer = search.viewer
        val rows = CommentTable.selectAll()
            .where {
                allOf(
                    CommentTable.postId eq search.postId,
                    CommentTable.deletedAt.isNull(),
                    CommentTable.parentCommentId.isNull(),
                    blockExclusion(viewer, CommentTable.authorId),
                    hiddenExclusion(viewer, CommentTable.hidden, CommentTable.authorId),
                    cursor?.let { keysetBefore(CommentTable.createdAt, CommentTable.id, it) },
                )
            }
            .orderBy(CommentTable.createdAt to SortOrder.DESC, CommentTable.id to SortOrder.DESC)
            .limit(search.first + 1)
            .toList()
        return page(rows, search.first, cursor != null) { row ->
            CursorEdge(encodeCursor(row[CommentTable.createdAt].toInstant(), row[CommentTable.id]), row.toCommentView(viewer.memberId))
        }
    }

    /** 글당 최신 [limitPerPost]건. 주의: 대상 글의 댓글을 전부 읽고 메모리에서 자른다 — 글당 댓글이 많아지면 바꿔야 한다. */
    override fun findCommentPreviews(postIds: List<Long>, limitPerPost: Int, viewer: ViewerContext): Map<Long, List<CommentView>> {
        if (postIds.isEmpty()) return emptyMap()
        val previews = mutableMapOf<Long, MutableList<CommentView>>()
        CommentTable.selectAll()
            .where {
                allOf(
                    CommentTable.postId inList postIds,
                    CommentTable.deletedAt.isNull(),
                    blockExclusion(viewer, CommentTable.authorId),
                    hiddenExclusion(viewer, CommentTable.hidden, CommentTable.authorId),
                )
            }
            .orderBy(CommentTable.createdAt to SortOrder.DESC, CommentTable.id to SortOrder.DESC)
            .forEach { row ->
                val bucket = previews.getOrPut(row[CommentTable.postId]) { mutableListOf() }
                if (bucket.size < limitPerPost) bucket += row.toCommentView(viewer.memberId)
            }
        return previews
    }

    /** 대댓글은 오래된 순이며 페이지네이션이 없다. */
    override fun findRepliesOf(parentCommentIds: List<Long>, viewer: ViewerContext): Map<Long, List<CommentView>> {
        if (parentCommentIds.isEmpty()) return emptyMap()
        return CommentTable.selectAll()
            .where {
                allOf(
                    CommentTable.parentCommentId inList parentCommentIds,
                    CommentTable.deletedAt.isNull(),
                    blockExclusion(viewer, CommentTable.authorId),
                    hiddenExclusion(viewer, CommentTable.hidden, CommentTable.authorId),
                )
            }
            .orderBy(CommentTable.createdAt to SortOrder.ASC, CommentTable.id to SortOrder.ASC)
            .groupBy({ requireNotNull(it[CommentTable.parentCommentId]) }, { it.toCommentView(viewer.memberId) })
    }
}
