package org.around.ieuri.community.application.block

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.community.application.shared.CommunityPolicy
import org.around.ieuri.community.application.shared.required.CommunityQueryPort
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockQueryServiceTest {

    private val queries = mockk<CommunityQueryPort>()
    private val service = BlockQueryService(queries, CommunityPolicy(50, 3, 100_000, Duration.ofMinutes(30), 10, setOf("image/jpeg")))

    @Test
    fun `차단 판정은 어느 방향이든 참이다`() {
        // 포트는 내가 차단한 쪽과 나를 차단한 쪽을 합쳐 돌려준다.
        every { queries.findBlockedMemberIds(7) } returns setOf(9L)

        assertTrue(service.isBlockedBetween(7, 9))
        assertFalse(service.isBlockedBetween(7, 8))
    }
}
