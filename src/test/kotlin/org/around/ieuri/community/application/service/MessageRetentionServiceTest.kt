package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.persistence.ExpiredMessagePurgeResult
import org.around.ieuri.community.application.port.out.persistence.MessagePurgePort
import org.around.ieuri.community.domain.aggregate.Message
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** DM 보관 기간 삭제(SPEC-0017 R16). */
class MessageRetentionServiceTest {

    private val now = Instant.parse("2026-08-12T00:00:00Z")

    /** AC16-1·AC16-3 — 기준 시각은 지금으로부터 보관 기간 이전이다. */
    @Test
    fun `삭제 배치는 보관 기간 이전 메시지를 회차 상한만큼 지운다`() {
        val purgePort = mockk<MessagePurgePort>()
        val storagePort = mockk<ObjectStoragePort>(relaxed = true)
        val clock = mockk<ClockPort> { every { now() } returns now }
        val service = MessageRetentionService(purgePort, storagePort, clock)
        every { purgePort.deleteCreatedBefore(any(), any()) } returns
            ExpiredMessagePurgeResult(deletedCount = 42, photoObjectKeys = emptyList())

        val deleted = service.purgeExpiredBatch(limit = 100)

        assertEquals(42, deleted)
        verify { purgePort.deleteCreatedBefore(Message.retentionCutoff(now), 100) }
    }

    /** AC16-1 — 첨부 사진 오브젝트도 지우며, 행 삭제 뒤에 지운다. */
    @Test
    fun `지운 메시지의 첨부 오브젝트도 지운다`() {
        val purgePort = mockk<MessagePurgePort>()
        val storagePort = mockk<ObjectStoragePort>(relaxed = true)
        val clock = mockk<ClockPort> { every { now() } returns now }
        val service = MessageRetentionService(purgePort, storagePort, clock)
        every { purgePort.deleteCreatedBefore(any(), any()) } returns
            ExpiredMessagePurgeResult(deletedCount = 2, photoObjectKeys = listOf("dm/1/a.jpg", "dm/2/b.jpg"))

        val deleted = service.purgeExpiredBatch(limit = 100)

        assertEquals(2, deleted)
        verifyOrder {
            purgePort.deleteCreatedBefore(any(), any())
            storagePort.delete("dm/1/a.jpg")
            storagePort.delete("dm/2/b.jpg")
        }
    }

    /** 한 장의 삭제 실패로 회차 전체를 되돌리지 않는다. */
    @Test
    fun `오브젝트 삭제가 실패해도 회차는 성공으로 끝난다`() {
        val purgePort = mockk<MessagePurgePort>()
        val storagePort = mockk<ObjectStoragePort>(relaxed = true)
        val clock = mockk<ClockPort> { every { now() } returns now }
        val service = MessageRetentionService(purgePort, storagePort, clock)
        every { purgePort.deleteCreatedBefore(any(), any()) } returns
            ExpiredMessagePurgeResult(deletedCount = 2, photoObjectKeys = listOf("dm/1/a.jpg", "dm/2/b.jpg"))
        every { storagePort.delete("dm/1/a.jpg") } throws RuntimeException("r2 down")

        val deleted = service.purgeExpiredBatch(limit = 100)

        assertEquals(2, deleted)
        // 앞의 실패가 뒤의 키까지 건너뛰게 하지 않는다.
        verify { storagePort.delete("dm/2/b.jpg") }
    }

    /** 지운 행이 없으면 스토리지를 부르지 않는다. */
    @Test
    fun `지운 메시지가 없으면 스토리지를 부르지 않는다`() {
        val purgePort = mockk<MessagePurgePort>()
        val storagePort = mockk<ObjectStoragePort>(relaxed = true)
        val clock = mockk<ClockPort> { every { now() } returns now }
        val service = MessageRetentionService(purgePort, storagePort, clock)
        every { purgePort.deleteCreatedBefore(any(), any()) } returns
            ExpiredMessagePurgeResult(deletedCount = 0, photoObjectKeys = emptyList())

        assertEquals(0, service.purgeExpiredBatch(limit = 100))

        verify(exactly = 0) { storagePort.delete(any()) }
    }

    @Test
    fun `회차 상한은 양수여야 한다`() {
        val service = MessageRetentionService(
            mockk<MessagePurgePort>(),
            mockk<ObjectStoragePort>(),
            mockk<ClockPort> { every { now() } returns now },
        )

        assertFailsWith<IllegalArgumentException> { service.purgeExpiredBatch(limit = 0) }
    }
}
