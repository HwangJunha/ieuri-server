package org.around.ieuri.community.domain.block

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlockTest {

    @Test
    fun `다른 회원을 차단한다`() {
        val block = Block(blockerId = 7, blockedId = 8)

        assertEquals(7L, block.blockerId)
        assertEquals(8L, block.blockedId)
    }

    @Test
    fun `자기 자신은 차단할 수 없다`() {
        assertFailsWith<CannotBlockSelfException> { Block(blockerId = 7, blockedId = 7) }
    }
}
