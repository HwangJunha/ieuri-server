package org.around.ieuri.auth.infrastructure.adapter.`in`.event

import org.around.ieuri.auth.application.port.out.persistence.LocationAccessLogPort
import org.around.ieuri.auth.infrastructure.config.AuthAsyncConfig
import org.around.ieuri.common.event.LocationAccessed
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 위치 조회 감사 기록(SPEC-0032 R19~R21, POL-057).
 *
 * **이벤트로 받는 이유는 감사가 요청의 결과를 바꾸지 않기 때문이다.** 조회한 도메인이 auth를
 * 직접 부르면 두 도메인이 붙고, 기록 실패가 조회 실패로 번질 여지가 생긴다. 발행자는 자기
 * 도메인 이벤트만 알고 auth는 그것을 구독한다 — `VerificationEmailListener`와 같은 형태다.
 *
 * **`@TransactionalEventListener`가 아니라 `@EventListener`다.** 조회 연산에는 트랜잭션이
 * 없거나 읽기 전용이라 커밋 시점을 기다릴 것이 없다. 커밋에 매달면 트랜잭션 밖에서 발행된
 * 이벤트가 조용히 버려진다.
 *
 * 남기는 것은 **회원 식별자 · 연산명 · 시각** 셋뿐이다. 닉네임·이메일을 복사해 두지 않는다 —
 * 복사본은 그 자체로 개인정보가 되고(POL-013), 회원 PK는 필요할 때 조인으로 풀린다.
 */
@Component
class LocationAccessAuditListener(
    private val locationAccessLogPort: LocationAccessLogPort,
) {

    @Async(AuthAsyncConfig.ACCESS_LOG_EXECUTOR)
    @EventListener
    fun on(event: LocationAccessed) {
        try {
            locationAccessLogPort.record(event.memberId, mapOf("operation" to event.operation))
        } catch (e: RuntimeException) {
            // **요청을 실패시키지 않는다** — 감사 기록 때문에 조회가 실패하면 안 된다.
            // 다만 조용히 버리지도 않는다. 좌표는 애초에 이벤트에 없으므로 로그에도 없다.
            log.warn("위치 조회 감사 기록에 실패했다: operation={}", event.operation, e)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(LocationAccessAuditListener::class.java)!!
    }
}
