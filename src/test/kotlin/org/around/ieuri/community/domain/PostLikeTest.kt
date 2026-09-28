package org.around.ieuri.community.domain

import org.around.ieuri.community.domain.aggregate.PostLike
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 좋아요 식별값의 유효성. 멱등성은 저장소의 유일 제약이 판정한다. */
class PostLikeTest {

    @Test
    fun `생성 시 id와 시각은 저장소가 채운다`() {
        val like = PostLike.create(postId = 1L, memberId = 2L)

        assertNull(like.id)
        assertNull(like.createdAt)
        assertEquals(1L, like.postId)
        assertEquals(2L, like.memberId)
    }

    @Test
    fun `식별자는 양수여야 한다`() {
        assertFailsWith<IllegalArgumentException> { PostLike.create(postId = 0L, memberId = 2L) }
        assertFailsWith<IllegalArgumentException> { PostLike.create(postId = -1L, memberId = 2L) }
        assertFailsWith<IllegalArgumentException> { PostLike.create(postId = 1L, memberId = 0L) }
        assertFailsWith<IllegalArgumentException> { PostLike.create(postId = 1L, memberId = -1L) }
    }

    @Test
    fun `복원은 필드마다 옮겨진다`() {
        val createdAt = Instant.parse("2026-08-07T00:00:00Z")

        val like = PostLike.restore(id = 9L, postId = 1L, memberId = 2L, createdAt = createdAt)

        assertEquals(9L, like.id)
        assertEquals(1L, like.postId)
        assertEquals(2L, like.memberId)
        assertEquals(createdAt, like.createdAt)
    }
}
