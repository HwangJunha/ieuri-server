package org.around.ieuri.tourism.application.port.out.client

/**
 * 외부 유행 점수 슬롯(SPEC-0035 R12). 값은 0~1이며 **키가 없는 카탈로그는 요인 없음**이다.
 * 출처는 후속 스펙이 정한다(T4) — 지금 어댑터는 항상 빈 map을 준다.
 */
interface ExternalTrendScorePort {
    fun findByCatalogIds(catalogIds: Collection<Long>): Map<Long, Double>
}
