package org.around.ieuri.tourism.infrastructure.adapter.out.client

import org.around.ieuri.tourism.application.port.out.client.ExternalTrendScorePort
import org.springframework.stereotype.Component

/**
 * 외부 유행 점수 슬롯의 빈 구현(SPEC-0035 R12·T4). 출처가 정해지면 이 어댑터만 바꾼다 —
 * 계약·점수식·재배열은 그대로다.
 */
@Component
class NoOpExternalTrendScoreAdapter : ExternalTrendScorePort {
    override fun findByCatalogIds(catalogIds: Collection<Long>): Map<Long, Double> = emptyMap()
}
