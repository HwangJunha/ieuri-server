package org.around.ieuri.auth.infrastructure.adapter.`in`.event

import org.around.ieuri.auth.application.port.out.persistence.AccessLogPort
import org.around.ieuri.auth.infrastructure.config.AuthAsyncConfig
import org.around.ieuri.common.event.MemberAccessed
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 접속기록(안전성 확보조치 기준 제8조).
 * - [Async]: 큐가 차면 `CallerRunsPolicy`로 호출 스레드가 저장하므로 유실되지 않는다.
 * - `@EventListener`: 로그인 트랜잭션이 뒤에서 실패해도 접속 시도는 남아야 한다.
 */
@Component
class MemberAccessAuditListener(
    private val accessLogPort: AccessLogPort,
) {

    @Async(AuthAsyncConfig.ACCESS_LOG_EXECUTOR)
    @EventListener
    fun on(event: MemberAccessed) {
        try {
            accessLogPort.record(
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
        val log = LoggerFactory.getLogger(MemberAccessAuditListener::class.java)!!
    }
}
