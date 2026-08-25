package org.around.ieuri.community.infrastructure.adapter.out.client

import org.around.ieuri.auth.application.port.`in`.command.LocationAuditUseCase
import org.around.ieuri.community.application.port.out.client.LocationAuditPort
import org.springframework.stereotype.Component

/**
 * community의 위치 감사 포트를 auth의 인바운드 포트로 번역한다(SPEC-0032 R19).
 *
 * 상대 도메인의 `port/in` 호출은 `out/client` 어댑터에서만 한다 —
 * `SpotLookupAdapter` → `SpotQueryUseCase`의 선례와 같다.
 *
 * 클래스명에 도메인을 붙인 이유는 tourism에 같은 역할의 어댑터가 있기 때문이다 —
 * 단순 클래스명이 겹치면 컴포넌트 스캔이 기동에서 실패한다(LayeredArchitectureTest).
 */
@Component
class CommunityLocationAuditAdapter(
    private val locationAuditUseCase: LocationAuditUseCase,
) : LocationAuditPort {

    override fun recordAccess(memberId: Long?, operation: String) =
        locationAuditUseCase.recordAccess(memberId, operation)
}
