package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import org.around.ieuri.community.application.port.out.persistence.PostCounterPort
import org.around.ieuri.community.application.port.out.persistence.PostModerationPort
import org.around.ieuri.community.domain.aggregate.Comment
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
    private val counterPort = mockk<PostCounterPort>(relaxed = true)
    private val moderationPort = mockk<PostModerationPort>()
    private val clock = mockk<ClockPort> { every { now() } returns now }

    private val service = CommentCommandService(
        commentRepository, counterPort, moderationPort, clock,
    )

    private fun givenPostExists(exists: Boolean = true) {
        every { moderationPort.exists(postId) } returns exists
    }

    private fun comment(
        authorId: Long = memberId,
        hidden: Boolean = false,
        id: Long = commentId,
        parentCommentId: Long? = null,
    ) = Comment.restore(
        id = id, postId = postId, parentCommentId = parentCommentId,
        authorId = authorId, body = "댓글",
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
        givenPostExists()
        givenSaveEchoesBackWithId()

        assertEquals(commentId, service.createComment(CreateCommentCommand(postId, "댓글"), memberId))

        // 계수는 DB가 원자적으로 올린다 — 애그리게이트를 복원하지 않는다.
        verify(exactly = 1) { counterPort.increaseCommentCount(postId) }
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
        givenPostExists(exists = false)

        assertFailsWith<PostNotFoundException> {
            service.createComment(CreateCommentCommand(postId, "댓글"), memberId)
        }
        verify(exactly = 0) { commentRepository.save(any()) }
    }

    // ---------- 대댓글 (깊이 2단계) ----------

    @Test
    fun `최상위 댓글에 답글을 달면 그 댓글이 부모다`() {
        givenPostExists()
        givenSaveEchoesBackWithId()
        val parent = comment(id = 5L)
        every { commentRepository.findById(5L) } returns parent
        val saved = slot<Comment>()
        every { commentRepository.save(capture(saved)) } answers {
            firstArg<Comment>().let {
                Comment.restore(
                    id = commentId, postId = it.postId, parentCommentId = it.parentCommentId,
                    authorId = it.authorId, body = it.body, hidden = it.hidden,
                    deletedAt = it.deletedAt, createdAt = now,
                )
            }
        }

        service.createComment(CreateCommentCommand(postId, "답글", parentCommentId = 5L), memberId)

        assertEquals(5L, saved.captured.parentCommentId)
    }

    @Test
    fun `대댓글에 답글을 달면 3단계가 되지 않고 최상위에 눕는다`() {
        givenPostExists()
        // 5번의 답글인 9번에 답글을 단다.
        val reply = comment(id = 9L, parentCommentId = 5L)
        every { commentRepository.findById(9L) } returns reply
        val saved = slot<Comment>()
        every { commentRepository.save(capture(saved)) } answers {
            firstArg<Comment>().let {
                Comment.restore(
                    id = commentId, postId = it.postId, parentCommentId = it.parentCommentId,
                    authorId = it.authorId, body = it.body, hidden = it.hidden,
                    deletedAt = it.deletedAt, createdAt = now,
                )
            }
        }

        service.createComment(CreateCommentCommand(postId, "답글의 답글", parentCommentId = 9L), memberId)

        // 거부하는 대신 부모의 부모(최상위)에 붙인다 — 9가 아니라 5다.
        assertEquals(5L, saved.captured.parentCommentId)
    }

    @Test
    fun `다른 글의 댓글은 부모가 될 수 없다`() {
        givenPostExists()
        // postId가 다른 댓글이다. 허용하면 글 A의 목록에 글 B의 답글이 섞인다.
        every { commentRepository.findById(5L) } returns Comment.restore(
            id = 5L, postId = postId + 999, authorId = memberId, body = "남의 글 댓글",
            hidden = false, deletedAt = null, createdAt = now,
        )

        assertFailsWith<CommentNotFoundException> {
            service.createComment(CreateCommentCommand(postId, "답글", parentCommentId = 5L), memberId)
        }
        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `없는 댓글에 답글을 달 수 없다`() {
        givenPostExists()
        every { commentRepository.findById(5L) } returns null

        assertFailsWith<CommentNotFoundException> {
            service.createComment(CreateCommentCommand(postId, "답글", parentCommentId = 5L), memberId)
        }
    }

    @Test
    fun `대댓글도 댓글 수에 포함된다`() {
        givenPostExists()
        givenSaveEchoesBackWithId()
        every { commentRepository.findById(5L) } returns comment(id = 5L)

        service.createComment(CreateCommentCommand(postId, "답글", parentCommentId = 5L), memberId)

        // "댓글 3개"인데 열어서 5개면 어긋나 보인다.
        verify(exactly = 1) { counterPort.increaseCommentCount(postId) }
    }

    @Test
    fun `본인 댓글을 삭제하면 글의 댓글 수가 1 감소한다`() {
        val target = comment()
        every { commentRepository.findById(commentId) } returns target
        every { commentRepository.save(any()) } returns target

        service.deleteComment(DeleteCommentCommand(commentId), memberId)

        assertTrue(target.deleted)
        verify(exactly = 1) { counterPort.decreaseCommentCount(postId) }
    }

    @Test
    fun `이미 숨겨진 댓글을 삭제해도 댓글 수는 그대로다 - 숨김 시점에 이미 내렸다`() {
        val target = comment(hidden = true)
        every { commentRepository.findById(commentId) } returns target
        every { commentRepository.save(any()) } returns target

        service.deleteComment(DeleteCommentCommand(commentId), memberId)

        assertTrue(target.deleted)
        // 두 번 내리면 숫자가 실제보다 작아진다.
        verify(exactly = 0) { counterPort.decreaseCommentCount(any()) }
    }

    @Test
    fun `타인의 댓글은 삭제할 수 없고 댓글 수도 변하지 않는다`() {
        val target = comment(authorId = memberId + 1)
        every { commentRepository.findById(commentId) } returns target

        assertFailsWith<NotCommentOwnerException> {
            service.deleteComment(DeleteCommentCommand(commentId), memberId)
        }
        assertFalse(target.deleted)
        verify(exactly = 0) { commentRepository.save(any()) }
        verify(exactly = 0) { counterPort.decreaseCommentCount(any()) }
    }

    @Test
    fun `존재하지 않는 댓글의 삭제는 COMMENT_NOT_FOUND다`() {
        every { commentRepository.findById(commentId) } returns null

        assertFailsWith<CommentNotFoundException> {
            service.deleteComment(DeleteCommentCommand(commentId), memberId)
        }
    }
}
