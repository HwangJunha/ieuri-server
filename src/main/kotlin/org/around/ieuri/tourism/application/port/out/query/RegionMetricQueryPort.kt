package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.OriginDistrict
import org.around.ieuri.tourism.application.query.RegionMetric
import org.around.ieuri.tourism.application.query.RegionRow
import org.around.ieuri.tourism.domain.vo.Coordinates

/**
 * 시군구 추천 지표 조회 아웃바운드 포트(SPEC-0015 R14·R20).
 *
 * 정규화·가중합은 여기서 하지 않는다 — 이 포트는 **원본 값**만 돌려주고, 정규화 기준이
 * 전체 집합에서 나오므로(설계 §3.3) 척도 결정은 애플리케이션 계층의 몫이다.
 *
 * **지표마다 메서드가 하나씩이다.** 집계 단위가 서로 달라(오늘 예측 평균 / 7일 평균 /
 * 최신 월) 하나의 질의로 묶으면 지역-지표 결합 규칙이 SQL에 숨는다. 결합은 코드가 한다.
 */
interface RegionMetricQueryPort {
    /**
     * 추천 후보 시군구 전건. **대표 위치가 없는 지역은 빠진다** — 갈 곳을 못 고른 지역이다.
     *
     * 지표가 하나도 없는 시군구도 여기에는 들어온다. 최소 항목 수(R30) 판정은 호출자 몫이다.
     */
    fun findRegions(): List<RegionRow>

    /**
     * origin 좌표를 품은 시군구. **경계 안에 있는 지역이 없으면 null**이다(바다·해외) — 오류가
     * 아니며, 호출자는 이때 전국 대상으로 되돌린다.
     *
     * 대표 위치 유무와 무관하게 경계로만 판정한다 — 자기 지역 판정은 후보 자격과 다른 질문이다.
     */
    fun findDistrictAt(origin: Coordinates): OriginDistrict?

    /** 시군구 코드 → 오늘 예측 집중률. 오늘 값이 없는 지역은 **키 자체가 없다**(결측, POL-043). */
    fun findConcentrationRates(): Map<String, RegionMetric>

    /** 시군구 코드 → 최근 7일 평균 방문자수(외지인+외국인). 값이 없는 지역은 키가 없다. */
    fun findVisitorCounts(): Map<String, RegionMetric>

    /** 시군구 코드 → 최신 월 체류 강도. 값이 없는 지역은 키가 없다. */
    fun findStayIntensities(): Map<String, RegionMetric>

    /** 시군구 코드 → 최신 월 소비 강도. 값이 없는 지역은 키가 없다. */
    fun findSpendingIntensities(): Map<String, RegionMetric>
}
