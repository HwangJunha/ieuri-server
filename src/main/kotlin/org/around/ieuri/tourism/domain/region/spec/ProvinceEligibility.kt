package org.around.ieuri.tourism.domain.region.spec

import org.around.ieuri.tourism.domain.shared.Specification

/** 시도가 지역 추천 후보 집합이 되는 조건(SPEC-0015). 후보는 자기 지역을 포함한 시군구 수이며 [minDistricts] 이상이어야 한다(경계값 포함). */
class ProvinceEligibility(private val minDistricts: Int) : Specification<Int> {
    override fun isSatisfiedBy(candidate: Int): Boolean = candidate >= minDistricts
}
