package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.port.out.client.ClockPort
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
        val clock = mockk<ClockPort> { every { now() } returns now }
        val service = MessageRetentionService(purgePort, clock)
        every { purgePort.deleteCreatedBefore(any(), any()) } returns 42

        val deleted = service.purgeExpiredBatch(limit = 100)

        assertEquals(42, deleted)
        verify { purgePort.deleteCreatedBefore(Message.retentionCutoff(now), 100) }
    }

    @Test
    fun `회차 상한은 양수여야 한다`() {
        val service = MessageRetentionService(
            mockk<MessagePurgePort>(),
            mockk<ClockPort> { every { now() } returns now },
        )

        assertFailsWith<IllegalArgumentException> { service.purgeExpiredBatch(limit = 0) }
    }
}
