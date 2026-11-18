package org.around.ieuri.community.application.comment

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.around.ieuri.community.application.comment.provided.dto.PostCommentsQuery
import org.around.ieuri.community.application.comment.required.CommentQueryPort
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.around.ieuri.community.application.shared.PagePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CommentQueryServiceTest {

    private val queries = mockk<CommentQueryPort>()
    private val posts = mockk<PostQueryUseCase>()
    private val blocks = mockk<BlockQueryUseCase> {
        every { blockedMemberIds(7) } returns setOf(9L)
    }
    private val policy = CommentPolicy(previewSize = 3)
    private val service = CommentQueryService(queries, posts, blocks, PagePolicy(maxPageSize = 50), policy)

    @Test
    fun `못 보는 글의 댓글은 오류가 아니라 빈 첫 페이지이고 댓글을 읽지 않는다`() {
        every { posts.isVisible(1, 7) } returns false

        val page = service.comments(PostCommentsQuery(postId = 1, first = 20, after = null), memberId = 7)

        assertEquals(0, page.edges.size)
        assertFalse(page.pageInfo.hasNextPage)
        assertFalse(page.pageInfo.hasPreviousPage)
        verify(exactly = 0) { queries.findComments(any()) }
    }

    @Test
    fun `댓글 미리보기는 정책의 글당 개수와 조회자의 차단 회원을 넘긴다`() {
        every { queries.findCommentPreviews(listOf(1, 2), 3, any()) } returns emptyMap()

        service.commentPreviews(listOf(1, 2), memberId = 7)

        verify { queries.findCommentPreviews(listOf(1, 2), policy.previewSize, match { it.memberId == 7L && it.blockedMemberIds == setOf(9L) }) }
    }
}
