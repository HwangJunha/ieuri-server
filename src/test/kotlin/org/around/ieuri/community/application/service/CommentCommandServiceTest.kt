package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.command.CreateCommentCommand
import org.around.ieuri.community.application.command.DeleteCommentCommand
import org.around.ieuri.community.application.exception.CommentBodyLengthInvalidException
import org.around.ieuri.community.application.exception.CommentBodyRequiredException
import org.around.ieuri.community.application.exception.CommentNotFoundException
import org.around.ieuri.community.application.exception.NotCommentOwnerException
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.persistence.CommentRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostRepositoryPort
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.vo.PostTarget
import org.around.ieuri.community.domain.vo.PostTargetType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 댓글 생성·삭제(R13, R23). 핵심은 글의 commentCount가 실제 보이는 댓글 수와 어긋나지 않는 것이다 —
 * 숨겨진 댓글은 이미 수에서 빠져 있으므로 삭제 시 **다시 내리지 않는다**.
 */
class CommentCommandServiceTest {

    private val memberId = 7L
    private val postId = 1L
    private val commentId = 2L
    private val now = Instant.parse("2026-08-05T00:00:00Z")

    private val commentRepository = mockk<CommentRepositoryPort>()
    private val postRepository = mockk<PostRepositoryPort>()
    private val clock = mockk<ClockPort> { every { now() } returns now }

    private val service = CommentCommandService(commentRepository, postRepository, clock)

    private fun post(commentCount: Int) = Post.restore(
        id = postId, target = PostTarget(PostTargetType.SPOT, 1L), authorId = 99L, tags = emptyList(),
        title = "제목", body = "본문", photoKeys = emptyList(), likeCount = 0, commentCount = commentCount,
        hidden = false, deletedAt = null, createdAt = now,
    )

    private fun comment(authorId: Long = memberId, hidden: Boolean = false) = Comment.restore(
        id = commentId, postId = postId, authorId = authorId, body = "댓글",
        hidden = hidden, deletedAt = null, createdAt = now,
    )

    private fun givenSaveEchoesBackWithId() {
        every { commentRepository.save(any()) } answers {
            val c = firstArg<Comment>()
            Comment.restore(
                id = commentId, postId = c.postId, authorId = c.authorId, body = c.body,
                hidden = c.hidden, deletedAt = c.deletedAt, createdAt = now,
            )
        }
    }

    @Test
    fun `댓글을 달면 글의 댓글 수가 1 증가한다`() {
        val parent = post(commentCount = 0)
        every { postRepository.findById(postId) } returns parent
        every { postRepository.save(any()) } returns parent
        givenSaveEchoesBackWithId()

        assertEquals(commentId, service.createComment(CreateCommentCommand(postId, "댓글"), memberId))
        assertEquals(1, parent.commentCount)
    }

    @Test
    fun `빈 본문과 501자 본문은 서로 다른 코드로 거부된다`() {
        assertFailsWith<CommentBodyRequiredException> {
            service.createComment(CreateCommentCommand(postId, "   "), memberId)
        }
        assertFailsWith<CommentBodyLengthInvalidException> {
            service.createComment(CreateCommentCommand(postId, "가".repeat(501)), memberId)
        }
        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `존재하지 않는 글에는 댓글을 달 수 없다`() {
        every { postRepository.findById(postId) } returns null

        assertFailsWith<PostNotFoundException> {
            service.createComment(CreateCommentCommand(postId, "댓글"), memberId)
        }
        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `본인 댓글을 삭제하면 글의 댓글 수가 1 감소한다`() {
        val target = comment()
        val parent = post(commentCount = 3)
        every { commentRepository.findById(commentId) } returns target
        every { commentRepository.save(any()) } returns target
        every { postRepository.findById(postId) } returns parent
        every { postRepository.save(any()) } returns parent

        service.deleteComment(DeleteCommentCommand(commentId), memberId)

        assertTrue(target.deleted)
        assertEquals(2, parent.commentCount)
    }

    @Test
    fun `이미 숨겨진 댓글을 삭제해도 댓글 수는 그대로다 - 숨김 시점에 이미 내렸다`() {
        val target = comment(hidden = true)
        val parent = post(commentCount = 3)
        every { commentRepository.findById(commentId) } returns target
        every { commentRepository.save(any()) } returns target
        every { postRepository.findById(postId) } returns parent

        service.deleteComment(DeleteCommentCommand(commentId), memberId)

        assertTrue(target.deleted)
        assertEquals(3, parent.commentCount)
        // 두 번 내리면 숫자가 실제보다 작아진다.
        verify(exactly = 0) { postRepository.save(any()) }
    }

    @Test
    fun `타인의 댓글은 삭제할 수 없고 댓글 수도 변하지 않는다`() {
        val target = comment(authorId = memberId + 1)
        val parent = post(commentCount = 3)
        every { commentRepository.findById(commentId) } returns target
        every { postRepository.findById(postId) } returns parent

        assertFailsWith<NotCommentOwnerException> {
            service.deleteComment(DeleteCommentCommand(commentId), memberId)
        }
        assertFalse(target.deleted)
        assertEquals(3, parent.commentCount)
        verify(exactly = 0) { commentRepository.save(any()) }
        verify(exactly = 0) { postRepository.save(any()) }
    }

    @Test
    fun `존재하지 않는 댓글의 삭제는 COMMENT_NOT_FOUND다`() {
        every { commentRepository.findById(commentId) } returns null

        assertFailsWith<CommentNotFoundException> {
            service.deleteComment(DeleteCommentCommand(commentId), memberId)
        }
    }
}
