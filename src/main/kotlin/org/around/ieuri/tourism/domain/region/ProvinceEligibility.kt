package org.around.ieuri.tourism.domain.region

/** 시도가 지역 추천 후보 집합이 되는 조건(SPEC-0015). 자기 지역을 포함한 시군구가 [minDistricts] 이상이어야 한다(경계값 포함). */
class ProvinceEligibility(private val minDistricts: Int) {
    fun isMetBy(districtCount: Int): Boolean = districtCount >= minDistricts
}
