package org.around.ieuri.community.application.block

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.community.application.block.required.BlockQueryPort
import org.around.ieuri.community.application.shared.PagePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockQueryServiceTest {

    private val queries = mockk<BlockQueryPort>()
    private val service = BlockQueryService(queries, PagePolicy(maxPageSize = 50))

    @Test
    fun `차단 판정은 어느 방향이든 참이다`() {
        // 포트는 내가 차단한 쪽과 나를 차단한 쪽을 합쳐 돌려준다.
        every { queries.findBlockedMemberIds(7) } returns setOf(9L)

        assertTrue(service.isBlockedBetween(7, 9))
        assertFalse(service.isBlockedBetween(7, 8))
        assertEquals(setOf(9L), service.blockedMemberIds(7))
    }
}
