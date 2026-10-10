package org.around.ieuri.auth.adapter.event.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.audit.provided.AuditLogUseCase
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import kotlin.test.Test

/** 접속기록(안전성 확보조치 기준 제8조). 회원·수행업무·접속지를 남기고(시각은 DB가 채운다), 기록 실패가 요청을 실패시키지 않는다. */
class MemberAccessAuditListenerTest {

    private val memberId = 7L
    private val auditLog = mockk<AuditLogUseCase>(relaxUnitFun = true)
    private val listener = MemberAccessAuditListener(auditLog)

    @Test
    fun `회원과 수행업무와 접속지를 남긴다`() {
        listener.on(MemberAccessed(memberId, AccessType.LOGIN, "203.0.113.7"))

        verify(exactly = 1) { auditLog.recordAccess(memberId = memberId, operation = "LOGIN", ipAddress = "203.0.113.7") }
    }

    /** 수행업무는 종류의 이름 그대로 남는다 — 조회할 때 이 값으로 거른다. */
    @Test
    fun `수행업무는 모든 종류가 이름 그대로 남는다`() {
        AccessType.entries.forEach { type ->
            listener.on(MemberAccessed(memberId, type, "203.0.113.7"))

            verify(exactly = 1) { auditLog.recordAccess(memberId, type.name, "203.0.113.7") }
        }
    }

    /** 프록시가 헤더를 주지 않으면 접속지를 채워 넣지 않는다(null). */
    @Test
    fun `접속지를 모르면 null로 남긴다`() {
        listener.on(MemberAccessed(memberId, AccessType.WITHDRAW, null))

        verify(exactly = 1) { auditLog.recordAccess(memberId, "WITHDRAW", null) }
    }

    /** 접속기록 저장이 실패해도 로그인·탈퇴는 실패하지 않는다. */
    @Test
    fun `저장이 실패해도 예외가 밖으로 나가지 않는다`() {
        every { auditLog.recordAccess(any(), any(), any()) } throws RuntimeException("db down")

        listener.on(MemberAccessed(memberId, AccessType.LOGIN, "203.0.113.7"))

        verify(exactly = 1) { auditLog.recordAccess(memberId, "LOGIN", "203.0.113.7") }
    }
}
