package org.around.ieuri.tourism.application.region.provided.dto

/** 순회 대상 하나. 방문자수는 지역이 없고, 강도는 시도만, 집중률은 시도와 시군구가 함께 필요하다. */
data class RegionMetricTarget(
    val provinceCode: String?,
    val districtCode: String?,
) {
    companion object {
        /** 지역 없이 한 번 부르는 대상(방문자수). */
        val NATIONWIDE = RegionMetricTarget(provinceCode = null, districtCode = null)
    }
}
