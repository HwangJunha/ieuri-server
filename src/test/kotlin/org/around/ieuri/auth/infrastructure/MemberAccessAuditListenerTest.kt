package org.around.ieuri.auth.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.port.out.persistence.AccessLogPort
import org.around.ieuri.auth.infrastructure.adapter.`in`.event.MemberAccessAuditListener
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import kotlin.test.Test

/**
 * 접속기록(「개인정보의 안전성 확보조치 기준」 제8조).
 *
 * 고정하는 계약은 둘이다 — **기준이 요구하는 넷을 남기고**(계정·수행업무·접속지·시각),
 * **기록 실패가 요청을 실패시키지 않는다**.
 */
class MemberAccessAuditListenerTest {

    private val memberId = 7L
    private val port = mockk<AccessLogPort>(relaxed = true)
    private val listener = MemberAccessAuditListener(port)

    @Test
    fun `회원과 수행업무와 접속지를 남긴다`() {
        listener.on(MemberAccessed(memberId, AccessType.LOGIN, "203.0.113.7"))

        verify(exactly = 1) { port.record(memberId, "LOGIN", "203.0.113.7") }
    }

    /**
     * 프록시가 헤더를 주지 않으면 접속지를 알 수 없다. **모르는 것을 채워 넣지 않는다** —
     * 사설 IP 따위로 메우면 기준을 충족하는 것처럼 보이면서 아무도 식별하지 못한다.
     */
    @Test
    fun `접속지를 모르면 null로 남긴다`() {
        listener.on(MemberAccessed(memberId, AccessType.WITHDRAW, null))

        verify(exactly = 1) { port.record(memberId, "WITHDRAW", null) }
    }

    /**
     * 접속기록 저장이 실패해도 로그인·탈퇴가 실패하면 안 된다 —
     * `LocationAccessAuditListener`와 같은 판단이다.
     */
    @Test
    fun `저장이 실패해도 예외가 밖으로 나가지 않는다`() {
        every { port.record(any(), any(), any()) } throws RuntimeException("db down")

        listener.on(MemberAccessed(memberId, AccessType.LOGIN, "203.0.113.7"))
    }
}
