package org.around.ieuri.auth.adapter.event.listener

import org.around.ieuri.auth.adapter.config.AuthAsyncConfig
import org.around.ieuri.auth.application.audit.provided.AuditLogUseCase
import org.around.ieuri.common.event.LocationAccessed
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 위치 조회 감사 기록(SPEC-0032 R19~R21, POL-057). 조회한 도메인의 이벤트를 구독한다.
 * - 트랜잭션 없는 조회에서도 발행되므로 `@TransactionalEventListener`가 아니라 `@EventListener`다.
 * - 회원 식별자·연산명·시각만 남긴다. 닉네임·이메일을 복사하지 않는다(POL-013).
 */
@Component
class LocationAccessAuditListener(
    private val auditLog: AuditLogUseCase,
) {

    @Async(AuthAsyncConfig.ACCESS_LOG_EXECUTOR)
    @EventListener
    fun on(event: LocationAccessed) {
        try {
            auditLog.recordLocationAccess(event.memberId, mapOf("operation" to event.operation))
        } catch (e: RuntimeException) {
            // 요청을 실패시키지 않되 로그로 남긴다. 좌표는 이벤트에 없으므로 로그에도 없다.
            log.warn("위치 조회 감사 기록에 실패했다: operation={}", event.operation, e)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(LocationAccessAuditListener::class.java)
    }
}
