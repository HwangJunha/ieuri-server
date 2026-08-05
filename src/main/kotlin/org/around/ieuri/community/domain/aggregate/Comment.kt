package org.around.ieuri.community.domain.aggregate

import java.time.Instant

/**
 * 글에 달린 댓글. 애그리게이트 루트.
 *
 * **부모 댓글을 가리키는 필드가 없다.** 1단계 댓글만이라는 결정을 구조로 고정한다 —
 * 필드를 두고 null로 비워 두면 나중에 누군가 채우기 시작한다.
 */
class Comment private constructor(
    val id: Long?,
    val postId: Long,
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

    /** 숨겨진 댓글은 작성자 본인에게만 보인다. */
    fun visibleTo(memberId: Long): Boolean = !deleted && (!hidden || ownedBy(memberId))

    companion object {
        const val BODY_MAX_LENGTH = 500

        fun create(postId: Long, authorId: Long, body: String): Comment {
            require(postId > 0) { "postId는 양수여야 합니다." }
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            val trimmed = body.trim()
            require(trimmed.isNotEmpty()) { "본문은 비어 있을 수 없습니다." }
            require(trimmed.length <= BODY_MAX_LENGTH) { "본문은 ${BODY_MAX_LENGTH}자를 넘을 수 없습니다." }
            return Comment(
                id = null,
                postId = postId,
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
        ): Comment = Comment(
            id = id,
            postId = postId,
            authorId = authorId,
            body = body,
            hidden = hidden,
            deletedAt = deletedAt,
            createdAt = createdAt,
        )
    }
}
