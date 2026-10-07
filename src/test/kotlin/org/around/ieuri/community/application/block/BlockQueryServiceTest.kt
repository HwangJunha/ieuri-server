package org.around.ieuri.community.application.block

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.block.provided.dto.BlockedUserView
import org.around.ieuri.community.application.block.provided.dto.BlockedUsersQuery
import org.around.ieuri.community.application.block.required.BlockQueryPort
import org.around.ieuri.community.application.lookup.provided.MemberLookupUseCase
import org.around.ieuri.community.application.shared.PagePolicy
import org.around.ieuri.community.domain.lookup.MemberSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockQueryServiceTest {

    private val queries = mockk<BlockQueryPort>()
    private val members = mockk<MemberLookupUseCase>()
    private val service = BlockQueryService(queries, members, PagePolicy(maxPageSize = 50))

    @Test
    fun `차단 판정은 어느 방향이든 참이다`() {
        // 포트는 내가 차단한 쪽과 나를 차단한 쪽을 합쳐 돌려준다.
        every { queries.findBlockedMemberIds(7) } returns setOf(9L)

        assertTrue(service.isBlockedBetween(7, 9))
        assertFalse(service.isBlockedBetween(7, 8))
        assertEquals(setOf(9L), service.blockedMemberIds(7))
    }

    @Test
    fun `차단 목록은 회원 정보를 한 번에 채우고, 없는 회원은 비워 둔다`() {
        val at = Instant.parse("2026-10-01T00:00:00Z")
        every { queries.findBlockedUsers(any()) } returns cursorPageOf(
            edges = listOf(
                CursorEdge("c1", BlockedUserView(memberId = 9, member = null, blockedAt = at)),
                CursorEdge("c2", BlockedUserView(memberId = 11, member = null, blockedAt = at)),
            ),
            hasNextPage = true,
            hasPreviousPage = false,
        )
        every { members.members(listOf(9L, 11L)) } returns mapOf(9L to MemberSummary(9, "상대", null))

        val page = service.blockedUsers(BlockedUsersQuery(first = 2, after = null), memberId = 7)

        assertEquals("상대", page.edges[0].node.member?.nickname)
        assertNull(page.edges[1].node.member)
        assertEquals(11L, page.edges[1].node.memberId)
        assertEquals(listOf("c1", "c2"), page.edges.map { it.cursor })
        assertTrue(page.pageInfo.hasNextPage)
    }
}
