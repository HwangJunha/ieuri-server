package org.around.ieuri.auth.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.port.out.persistence.AccessLogPort
import org.around.ieuri.auth.infrastructure.adapter.`in`.event.MemberAccessAuditListener
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import kotlin.test.Test

/** 접속기록(안전성 확보조치 기준 제8조). 네 항목을 남기고, 기록 실패가 요청을 실패시키지 않는다. */
class MemberAccessAuditListenerTest {

    private val memberId = 7L
    private val port = mockk<AccessLogPort>(relaxed = true)
    private val listener = MemberAccessAuditListener(port)

    @Test
    fun `회원과 수행업무와 접속지를 남긴다`() {
        listener.on(MemberAccessed(memberId, AccessType.LOGIN, "203.0.113.7"))

        verify(exactly = 1) { port.record(memberId, "LOGIN", "203.0.113.7") }
    }

    /** 프록시가 헤더를 주지 않으면 접속지를 채워 넣지 않는다(null). */
    @Test
    fun `접속지를 모르면 null로 남긴다`() {
        listener.on(MemberAccessed(memberId, AccessType.WITHDRAW, null))

        verify(exactly = 1) { port.record(memberId, "WITHDRAW", null) }
    }

    /** 접속기록 저장이 실패해도 로그인·탈퇴는 실패하지 않는다. */
    @Test
    fun `저장이 실패해도 예외가 밖으로 나가지 않는다`() {
        every { port.record(any(), any(), any()) } throws RuntimeException("db down")

        listener.on(MemberAccessed(memberId, AccessType.LOGIN, "203.0.113.7"))
    }
}
