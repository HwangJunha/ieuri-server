package org.around.ieuri.community.application.block

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.block.required.BlockRepository
import org.around.ieuri.community.application.shared.exception.MemberNotFoundException
import org.around.ieuri.community.application.shared.required.MemberLookupPort
import org.around.ieuri.community.application.shared.required.dto.MemberSummaryView
import org.around.ieuri.community.domain.block.Block
import org.around.ieuri.community.domain.block.CannotBlockSelfException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlockModifyServiceTest {

    private val blocks = mockk<BlockRepository>()
    private val members = mockk<MemberLookupPort>()
    private val service = BlockModifyService(blocks, members)

    @Test
    fun `자기 차단은 회원 조회보다 먼저 거부한다`() {
        assertFailsWith<CannotBlockSelfException> { service.block(targetMemberId = 7, memberId = 7) }
        verify(exactly = 0) { members.findAuthor(any()) }
    }

    @Test
    fun `없는 회원은 차단하지 않고, 있으면 차단을 저장한다`() {
        every { members.findAuthor(8) } returns null
        assertFailsWith<MemberNotFoundException> { service.block(8, 7) }

        every { members.findAuthor(9) } returns MemberSummaryView(9, "상대", null)
        every { blocks.add(Block(7, 9)) } returns true
        service.block(9, 7)
        verify { blocks.add(Block(blockerId = 7, blockedId = 9)) }
    }

    @Test
    fun `탈퇴 정리는 회원이 걸었거나 회원을 향한 차단을 모두 지운다`() {
        every { blocks.removeInvolving(7) } returns 3

        assertEquals(3, service.eraseMemberContent(7, Instant.EPOCH))
    }
}
