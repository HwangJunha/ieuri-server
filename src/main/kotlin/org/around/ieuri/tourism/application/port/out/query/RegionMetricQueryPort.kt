package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.OriginDistrict
import org.around.ieuri.tourism.application.query.RegionMetric
import org.around.ieuri.tourism.application.query.RegionRow
import org.around.ieuri.tourism.domain.vo.Coordinates

/**
 * 시군구 추천 지표 조회 아웃바운드 포트(SPEC-0015). 정규화 이전의 원본 값만 돌려준다.
 * - [findDistrictAt]으로 시도를 먼저 알아낸 뒤 나머지를 부른다.
 */
interface RegionMetricQueryPort {
    /** origin 좌표를 품은 시군구를 경계로만 판정한다. 없으면(바다·해외) 오류가 아니라 null. */
    fun findDistrictAt(origin: Coordinates): OriginDistrict?

    /**
     * 그 시도의 추천 후보 시군구 전건. 대표 위치가 없는 지역은 빠진다.
     * - 지표가 없는 시군구도 들어온다. 최소 항목 수 판정은 호출자 몫이다.
     */
    fun findRegions(provinceCode: String): List<RegionRow>

    /** 시군구 코드 → 오늘 예측 집중률. 오늘 값이 없는 지역은 **키 자체가 없다**(결측, POL-043). */
    fun findConcentrationRates(provinceCode: String): Map<String, RegionMetric>

    /** 시군구 코드 → 최근 7일 평균 방문자수(외지인+외국인). 값이 없는 지역은 키가 없다. */
    fun findVisitorCounts(provinceCode: String): Map<String, RegionMetric>

    /** 시군구 코드 → 최신 월 체류 강도. 값이 없는 지역은 키가 없다. */
    fun findStayIntensities(provinceCode: String): Map<String, RegionMetric>

    /** 시군구 코드 → 최신 월 소비 강도. 값이 없는 지역은 키가 없다. */
    fun findSpendingIntensities(provinceCode: String): Map<String, RegionMetric>
}
