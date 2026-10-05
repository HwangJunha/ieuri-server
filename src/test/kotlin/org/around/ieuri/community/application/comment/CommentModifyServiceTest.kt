package org.around.ieuri.community.application.comment

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.comment.provided.CommentNotFoundException
import org.around.ieuri.community.application.comment.provided.dto.CreateCommentCommand
import org.around.ieuri.community.application.comment.required.CommentRepository
import org.around.ieuri.community.application.post.provided.exception.PostNotFoundException
import org.around.ieuri.community.application.post.required.PostRepository
import org.around.ieuri.community.application.shared.required.ClockPort
import org.around.ieuri.community.domain.comment.Comment
import org.around.ieuri.community.domain.comment.NotCommentOwnerException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommentModifyServiceTest {

    private val memberId = 7L
    private val now = Instant.parse("2026-10-05T00:00:00Z")
    private val commentRepository = mockk<CommentRepository>()
    private val postRepository = mockk<PostRepository>(relaxUnitFun = true)
    private val clockPort = mockk<ClockPort> { every { now() } returns now }
    private val service = CommentModifyService(commentRepository, postRepository, clockPort)

    private val comment = Comment(id = 10, postId = 1, parentCommentId = null, authorId = memberId, body = "댓글", hidden = false, deletedAt = null)

    @Test
    fun `댓글을 쓰면 글의 댓글 수를 올린다`() {
        every { postRepository.exists(1) } returns true
        every { commentRepository.insert(any()) } returns 11

        assertEquals(11L, service.createComment(CreateCommentCommand(postId = 1, body = "안녕"), memberId))
        verify { postRepository.increaseCommentCount(1) }
    }

    @Test
    fun `없는 글이나 다른 글의 부모 댓글은 거부하고 수를 바꾸지 않는다`() {
        every { postRepository.exists(2) } returns false
        assertFailsWith<PostNotFoundException> { service.createComment(CreateCommentCommand(postId = 2, body = "x"), memberId) }

        every { postRepository.exists(1) } returns true
        every { commentRepository.findById(20) } returns comment.copy(id = 20, postId = 9)
        assertFailsWith<CommentNotFoundException> { service.createComment(CreateCommentCommand(postId = 1, body = "x", parentCommentId = 20), memberId) }

        verify(exactly = 0) { postRepository.increaseCommentCount(any()) }
    }

    @Test
    fun `수에 잡혀 있던 댓글을 지우면 글의 댓글 수를 내린다`() {
        every { commentRepository.findById(10) } returns comment
        every { commentRepository.markDeletedWasCounted(10, now) } returns true

        service.deleteComment(10, memberId)

        verify(exactly = 1) { postRepository.decreaseCommentCount(1) }
    }

    @Test
    fun `숨겨졌거나 이미 지워진 댓글은 수를 내리지 않는다`() {
        every { commentRepository.findById(10) } returns comment
        every { commentRepository.markDeletedWasCounted(10, now) } returns false

        service.deleteComment(10, memberId)

        verify(exactly = 0) { postRepository.decreaseCommentCount(any()) }
    }

    @Test
    fun `남의 댓글은 지울 수 없다`() {
        every { commentRepository.findById(10) } returns comment

        assertFailsWith<NotCommentOwnerException> { service.deleteComment(10, memberId = 8) }
        verify(exactly = 0) { commentRepository.markDeletedWasCounted(any(), any()) }
    }

    @Test
    fun `탈퇴 정리는 사용자 삭제와 같은 경로로 하나씩 지운다`() {
        every { commentRepository.findIdsByAuthor(memberId) } returns listOf(10, 12)
        every { commentRepository.findById(10) } returns comment
        every { commentRepository.findById(12) } returns comment.copy(id = 12, postId = 2, hidden = true)
        every { commentRepository.markDeletedWasCounted(10, now) } returns true
        every { commentRepository.markDeletedWasCounted(12, now) } returns false

        assertEquals(2, service.eraseMemberContent(memberId, now))

        verify(exactly = 1) { postRepository.decreaseCommentCount(1) }
        verify(exactly = 0) { postRepository.decreaseCommentCount(2) }
    }
}
