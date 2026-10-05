package org.around.ieuri.community.application.comment

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.shared.CommunityPolicy
import org.around.ieuri.community.application.shared.dto.PostCommentsQuery
import org.around.ieuri.community.application.shared.required.CommunityQueryPort
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CommentQueryServiceTest {

    private val queries = mockk<CommunityQueryPort> {
        every { findBlockedMemberIds(7) } returns emptySet()
        every { findHiddenPostIds(7) } returns emptySet()
    }
    private val policy = CommunityPolicy(50, 3, 100_000, Duration.ofMinutes(30), 10, setOf("image/jpeg"))
    private val service = CommentQueryService(queries, policy)

    @Test
    fun `못 보는 글의 댓글은 오류가 아니라 빈 첫 페이지이고 댓글을 읽지 않는다`() {
        every { queries.findPost(1, any()) } returns null

        val page = service.comments(PostCommentsQuery(postId = 1, first = 20, after = null), memberId = 7)

        assertEquals(0, page.edges.size)
        assertFalse(page.pageInfo.hasNextPage)
        assertFalse(page.pageInfo.hasPreviousPage)
        verify(exactly = 0) { queries.findComments(any()) }
    }

    @Test
    fun `댓글 미리보기는 정책의 글당 개수를 넘긴다`() {
        every { queries.findCommentPreviews(listOf(1, 2), 3, any()) } returns emptyMap()

        service.commentPreviews(listOf(1, 2), memberId = 7)

        verify { queries.findCommentPreviews(listOf(1, 2), policy.commentPreviewSize, any()) }
    }
}
