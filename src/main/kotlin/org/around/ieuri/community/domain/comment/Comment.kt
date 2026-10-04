package org.around.ieuri.community.domain.comment

import org.around.ieuri.community.domain.shared.CommunityUserException
import java.time.Instant

class CommentBodyRequiredException(message: String = "댓글을 입력해 주세요.") : CommunityUserException("COMMENT_BODY_REQUIRED", message)

class CommentBodyLengthInvalidException(message: String = "댓글은 500자를 넘을 수 없습니다.") : CommunityUserException("COMMENT_BODY_LENGTH_INVALID", message)

class NotCommentOwnerException(message: String = "본인의 댓글만 삭제할 수 있습니다.") : CommunityUserException("NOT_COMMENT_OWNER", message)

/**
 * 글에 달린 댓글. 애그리게이트 루트.
 * - 깊이는 2단계까지다. 대댓글에 단 답글은 거부하지 않고 그 최상위 댓글 아래로 붙는다.
 * - 숨김(신고 누적)·삭제는 저장소의 조건부 UPDATE로 바뀐다.
 */
data class Comment(
    val id: Long?,
    val postId: Long,
    /** 최상위 댓글이면 null. 있으면 그 댓글에 달린 대댓글이다. */
    val parentCommentId: Long?,
    val authorId: Long,
    val body: String,
    val hidden: Boolean,
    val deletedAt: Instant?,
) {
    /** 작성자만 삭제할 수 있다. */
    fun delete(memberId: Long, now: Instant): Comment {
        if (authorId != memberId) throw NotCommentOwnerException()
        return copy(deletedAt = now)
    }

    companion object {
        const val BODY_MAX_LENGTH = 500

        /** [parent]가 있으면 답글이다. 같은 글의 댓글인지는 호출자가 확인한다. */
        fun write(postId: Long, authorId: Long, body: String, parent: Comment?): Comment {
            require(postId > 0) { "postId는 양수여야 합니다." }
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            val trimmed = body.trim()
            if (trimmed.isEmpty()) throw CommentBodyRequiredException()
            if (trimmed.length > BODY_MAX_LENGTH) throw CommentBodyLengthInvalidException()
            return Comment(
                id = null,
                postId = postId,
                parentCommentId = parent?.let { it.parentCommentId ?: requireNotNull(it.id) { "적재된 댓글에는 id가 있어야 한다." } },
                authorId = authorId,
                body = trimmed,
                hidden = false,
                deletedAt = null,
            )
        }
    }
}
