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

/** DM 보관 기간 삭제(SPEC-0017 R16). 피드가 사라져도 그대로 남는 경로다(SPEC-0021). */
class MessageRetentionServiceTest {

    private val now = Instant.parse("2026-08-12T00:00:00Z")

    /**
     * AC16-1 — **기준 시각은 지금으로부터 보관 기간 이전이다.** 대화의 활동과 무관하게
     * 메시지 자신의 생성 시각으로만 판정한다(AC16-3).
     */
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

    /**
     * AC16-1 — 행만 지우고 오브젝트를 남기면 "6개월 뒤 삭제"가 사진에 대해서는 거짓이 된다.
     *
     * **행 삭제 뒤에 지운다.** 먼저 지웠다가 트랜잭션이 롤백되면 메시지는 남고 사진만 사라져
     * 깨진 첨부가 된다 — 반대 순서의 최악은 고아 오브젝트이고 그쪽이 낫다.
     */
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

    /**
     * 한 장의 삭제 실패로 회차 전체를 되돌리지 않는다 — 스토리지에 이미 없는 키도 정상
     * 경로다(멱등). 남는 것은 고아 오브젝트뿐이고, 행은 이미 지워진 상태가 맞다.
     */
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

    /** 지운 행이 없으면 스토리지를 부르지 않는다 — 키가 있을 수 없다. */
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
