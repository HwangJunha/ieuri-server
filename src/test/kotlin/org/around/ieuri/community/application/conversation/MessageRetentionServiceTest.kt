package org.around.ieuri.community.application.conversation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.conversation.provided.ExpiredMessageDeleteUseCase
import org.around.ieuri.community.application.conversation.provided.dto.ExpiredMessages
import org.around.ieuri.community.application.shared.required.ObjectStoragePort
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageRetentionServiceTest {

    private val rows = mockk<ExpiredMessageDeleteUseCase>()
    private val storage = mockk<ObjectStoragePort>(relaxUnitFun = true)
    private val service = MessageRetentionService(rows, storage)

    @Test
    fun `행을 지운 뒤 그 사진을 지우고 지운 행 수를 돌려준다`() {
        every { rows.deleteExpiredMessages(100) } returns ExpiredMessages(deletedCount = 3, photoObjectKeys = listOf("a", "b"))

        assertEquals(3, service.purgeExpiredBatch(100))
        verify { storage.delete("a") }
        verify { storage.delete("b") }
    }

    @Test
    fun `사진 하나를 지우지 못해도 나머지를 지우고 건수는 그대로다`() {
        every { rows.deleteExpiredMessages(100) } returns ExpiredMessages(deletedCount = 2, photoObjectKeys = listOf("a", "b"))
        every { storage.delete("a") } throws IllegalStateException("R2 down")

        assertEquals(2, service.purgeExpiredBatch(100))
        verify { storage.delete("b") }
    }
}
