package org.around.ieuri.community.domain.comment

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CommentTest {

    private val now = Instant.parse("2026-10-05T00:00:00Z")
    private val topLevel = Comment(id = 10, postId = 1, parentCommentId = null, authorId = 7, body = "댓글", hidden = false, deletedAt = null)

    @Test
    fun `새 댓글은 본문을 트리밍하고 부모가 없으면 최상위다`() {
        val written = Comment.write(postId = 1, authorId = 7, body = "  안녕  ", parent = null)

        assertEquals(1L, written.postId)
        assertNull(written.parentCommentId)
        assertEquals("안녕", written.body)
        assertEquals(false, written.hidden)
    }

    @Test
    fun `대댓글은 부모 아래에, 대댓글에 단 답글은 그 최상위 댓글 아래로 붙는다`() {
        val reply = topLevel.copy(id = 11, parentCommentId = 10)

        assertEquals(10L, Comment.write(1, 8, "답글", parent = topLevel).parentCommentId)
        assertEquals(10L, Comment.write(1, 8, "답글의 답글", parent = reply).parentCommentId)
    }

    @Test
    fun `본문은 비거나 500자를 넘으면 거부한다`() {
        assertFailsWith<CommentBodyRequiredException> { Comment.write(1, 7, "  ", null) }
        assertFailsWith<CommentBodyLengthInvalidException> { Comment.write(1, 7, "가".repeat(Comment.BODY_MAX_LENGTH + 1), null) }
    }

    @Test
    fun `삭제는 작성자만 한다`() {
        assertEquals(now, topLevel.delete(7, now).deletedAt)
        assertFailsWith<NotCommentOwnerException> { topLevel.delete(8, now) }
    }
}
