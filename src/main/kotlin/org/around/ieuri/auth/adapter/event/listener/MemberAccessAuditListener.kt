package org.around.ieuri.auth.adapter.event.listener

import org.around.ieuri.auth.adapter.config.AuthAsyncConfig
import org.around.ieuri.auth.application.audit.provided.AuditLogCommandUseCase
import org.around.ieuri.common.event.MemberAccessed
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 접속기록(안전성 확보조치 기준 제8조).
 * - `@EventListener`: 로그인 요청이 뒤에서 실패해도 접속 시도는 남아야 한다.
 */
@Component
class MemberAccessAuditListener(
    private val auditLog: AuditLogCommandUseCase,
) {

    @Async(AuthAsyncConfig.ACCESS_LOG_EXECUTOR)
    @EventListener
    fun on(event: MemberAccessed) {
        try {
            auditLog.recordAccess(
                memberId = event.memberId,
                operation = event.accessType.name,
                ipAddress = event.ipAddress,
            )
        } catch (e: RuntimeException) {
            // 요청을 실패시키지 않되 로그로 남긴다(접속기록이 비면 침해사고 조사가 불가능하다). 개인정보는 담지 않는다(POL-013).
            log.warn("접속기록 저장에 실패했다: accessType={}", event.accessType, e)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(MemberAccessAuditListener::class.java)
    }
}
