package org.around.ieuri.community.domain.aggregate

import java.time.Instant

/**
 * 글에 달린 댓글. 애그리게이트 루트.
 * - 깊이는 2단계까지다. 대댓글에 단 답글은 거부하지 않고 최상위 댓글 아래로 붙는다([replyParentIdFor]).
 */
class Comment private constructor(
    val id: Long?,
    val postId: Long,
    /** 최상위 댓글이면 null. 있으면 그 댓글에 달린 대댓글이다. */
    val parentCommentId: Long?,
    val authorId: Long,
    val body: String,
    hidden: Boolean,
    deletedAt: Instant?,
    val createdAt: Instant?,
) {
    var hidden: Boolean = hidden
        private set

    var deletedAt: Instant? = deletedAt
        private set

    val deleted: Boolean get() = deletedAt != null

    fun hide() {
        hidden = true
    }

    /** 작성자 본인만 삭제할 수 있다. 소유자가 아니면 false이며 상태는 그대로다. */
    fun deleteBy(memberId: Long, now: Instant): Boolean {
        if (authorId != memberId) return false
        deletedAt = now
        return true
    }

    fun ownedBy(memberId: Long): Boolean = authorId == memberId

    /** 대댓글인지. */
    val isReply: Boolean get() = parentCommentId != null

    /** 숨겨진 댓글은 작성자 본인에게만 보인다. */
    fun visibleTo(memberId: Long): Boolean = !deleted && (!hidden || ownedBy(memberId))

    companion object {
        const val BODY_MAX_LENGTH = 500

        /** 답글이 실제로 붙을 부모의 id. 대댓글에 답하면 그 대댓글의 부모(최상위)가 부모다. */
        fun replyParentIdFor(parent: Comment): Long = parent.parentCommentId
            ?: requireNotNull(parent.id) { "적재된 댓글에는 id가 있어야 한다." }

        fun create(
            postId: Long,
            authorId: Long,
            body: String,
            parentCommentId: Long? = null,
        ): Comment {
            require(postId > 0) { "postId는 양수여야 합니다." }
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            require(parentCommentId == null || parentCommentId > 0) {
                "parentCommentId는 양수여야 합니다."
            }
            val trimmed = body.trim()
            require(trimmed.isNotEmpty()) { "본문은 비어 있을 수 없습니다." }
            require(trimmed.length <= BODY_MAX_LENGTH) { "본문은 ${BODY_MAX_LENGTH}자를 넘을 수 없습니다." }
            return Comment(
                id = null,
                postId = postId,
                parentCommentId = parentCommentId,
                authorId = authorId,
                body = trimmed,
                hidden = false,
                deletedAt = null,
                createdAt = null,
            )
        }

        fun restore(
            id: Long,
            postId: Long,
            authorId: Long,
            body: String,
            hidden: Boolean,
            deletedAt: Instant?,
            createdAt: Instant,
            parentCommentId: Long? = null,
        ): Comment = Comment(
            id = id,
            postId = postId,
            parentCommentId = parentCommentId,
            authorId = authorId,
            body = body,
            hidden = hidden,
            deletedAt = deletedAt,
            createdAt = createdAt,
        )
    }
}
