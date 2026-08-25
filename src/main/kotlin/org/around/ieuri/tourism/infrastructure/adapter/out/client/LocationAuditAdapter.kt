package org.around.ieuri.tourism.infrastructure.adapter.out.client

import org.around.ieuri.auth.application.port.`in`.command.LocationAuditUseCase
import org.around.ieuri.tourism.application.port.out.client.LocationAuditPort
import org.springframework.stereotype.Component

/**
 * tourism의 위치 감사 포트를 auth의 인바운드 포트로 번역한다(SPEC-0032 R19).
 *
 * **이것이 도메인 간 상호작용의 정해진 경로다** — 상대 도메인의 `port/in` 호출은 `out/client`
 * 어댑터에서만 한다(`SpotLookupAdapter` → `SpotQueryUseCase`의 선례). 리졸버가 auth를 직접
 * 부르면 두 도메인이 붙어 추출이 불가능해진다.
 */
@Component
class LocationAuditAdapter(
    private val locationAuditUseCase: LocationAuditUseCase,
) : LocationAuditPort {

    override fun recordAccess(memberId: Long?, operation: String) =
        locationAuditUseCase.recordAccess(memberId, operation)
}
