package org.around.ieuri.auth.infrastructure.adapter.`in`.event

import org.around.ieuri.auth.application.port.out.persistence.AccessLogPort
import org.around.ieuri.auth.infrastructure.config.AuthAsyncConfig
import org.around.ieuri.common.event.MemberAccessed
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 접속기록(「개인정보의 안전성 확보조치 기준」 제8조).
 *
 * **이벤트로 받는 이유는 기록이 요청의 결과를 바꾸지 않기 때문이다** —
 * `LocationAccessAuditListener`와 같은 판단이다. 발행자는 자기 일만 알고 auth가 그것을 구독한다.
 *
 * **[Async]인 것이 저 리스너와 다른 점이다.** 로그인은 사용자가 응답을 기다리는 연산이라
 * 기록 INSERT를 응답 경로에 두면 그만큼 느려진다. 위치 감사와 달리 발행량이 적어(로그인·탈퇴
 * 등 계정 사건뿐) 별도 스레드로 넘겨도 밀리지 않는다.
 *
 * `@TransactionalEventListener`가 아니라 `@EventListener`인 것도 같다 — 접속 사실은 트랜잭션의
 * 성패와 무관하게 남아야 한다. 로그인 트랜잭션이 뒤에서 실패해도 **접속 시도는 있었다.**
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
            // **요청을 실패시키지 않는다.** 다만 조용히 버리지도 않는다 — 접속기록이 비면
            // 침해사고 조사가 불가능해진다. 개인정보는 담지 않는다(POL-013).
            log.warn("접속기록 저장에 실패했다: accessType={}", event.accessType, e)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(MemberAccessAuditListener::class.java)!!
    }
}
