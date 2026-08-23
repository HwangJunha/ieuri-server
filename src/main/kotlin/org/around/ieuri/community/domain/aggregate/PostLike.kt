package org.around.ieuri.community.domain.aggregate

import java.time.Instant

/**
 * 글 좋아요. 애그리게이트 루트이며 식별은 `(postId, memberId)`다.
 *
 * 멱등성은 저장소의 유일 제약이 판정한다 — 이미 좋아요한 글에 다시 눌러도 오류가 아니라
 * no-op이다. tourism의 SpotReaction을 재사용하지 않는 이유는 그쪽이 spot에 대한
 * LIKE/DISLIKE 양방향 취향 기록이고 이쪽은 글에 대한 단방향 좋아요이기 때문이다.
 */
class PostLike private constructor(
    val id: Long?,
    val postId: Long,
    val memberId: Long,
    val createdAt: Instant?,
) {
    companion object {
        fun create(postId: Long, memberId: Long): PostLike {
            require(postId > 0) { "postId는 양수여야 합니다." }
            require(memberId > 0) { "memberId는 양수여야 합니다." }
            return PostLike(id = null, postId = postId, memberId = memberId, createdAt = null)
        }

        fun restore(id: Long, postId: Long, memberId: Long, createdAt: Instant): PostLike =
            PostLike(id = id, postId = postId, memberId = memberId, createdAt = createdAt)
    }
}
