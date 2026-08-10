package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.RegionMetricRow

/**
 * 시군구 추천 지표 조회 아웃바운드 포트(SPEC-0015 R14·R20).
 *
 * 정규화·가중합은 여기서 하지 않는다 — 이 포트는 **원본 값**만 돌려주고, 정규화 기준이
 * 전체 집합에서 나오므로(설계 §3.3) 척도 결정은 애플리케이션 계층의 몫이다.
 */
interface RegionMetricQueryPort {
    /**
     * 시군구 전건(252건)의 최신 지표 행.
     *
     * **지표가 하나도 없는 시군구도 행으로 온다** — 네 지표가 전부 null인 행이며, 호출자가
     * 최소 항목 수(R30)로 걸러낸다. 결측을 0으로 채우지 않는다(POL-043).
     */
    fun findLatestMetrics(): List<RegionMetricRow>
}
