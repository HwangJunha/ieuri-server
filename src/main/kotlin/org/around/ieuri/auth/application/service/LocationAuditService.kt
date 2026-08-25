package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.policy.LocationAuditPolicy
import org.around.ieuri.auth.application.port.`in`.command.LocationAuditUseCase
import org.around.ieuri.auth.application.port.out.persistence.LocationAccessLogPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 위치 조회 감사 기록(SPEC-0032 R19~R21, POL-057).
 *
 * 남기는 것은 **회원 식별자 · 연산명 · 시각** 셋뿐이다. 닉네임·이메일·provider 식별자를
 * 복사해 두지 않는다 — 복사본은 그 자체로 개인정보가 되고(POL-013), 회원 PK는 필요할 때
 * 조인으로 풀린다.
 */
@Service
class LocationAuditService(
    private val locationAccessLogPort: LocationAccessLogPort,
    private val policy: LocationAuditPolicy,
) : LocationAuditUseCase {

    override fun recordAccess(memberId: Long?, operation: String) {
        // R21. 신고 완료 전에는 기록 자체를 만들지 않는다.
        if (!policy.enabled) return
        // 비로그인은 남길 "누구"가 없다.
        if (memberId == null) return

        try {
            locationAccessLogPort.record(memberId, mapOf("operation" to operation))
        } catch (e: RuntimeException) {
            // **요청을 실패시키지 않는다** — 감사 기록 때문에 조회가 실패하면 안 된다.
            // 다만 조용히 버리지도 않는다. 좌표는 애초에 이 경로에 없으므로 로그에도 없다.
            log.warn("위치 조회 감사 기록에 실패했다: operation={}", operation, e)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(LocationAuditService::class.java)!!
    }
}
