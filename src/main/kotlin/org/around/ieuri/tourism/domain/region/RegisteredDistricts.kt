package org.around.ieuri.tourism.domain.region

/**
 * 지표를 받을 수 있는 시군구(SPEC-0015 R9). 우리 시군구 표에 있는 코드만 적재한다.
 * - 모르는 코드는 오류가 아니라 건너뛴다 — 개편 직후의 코드가 올 수 있다.
 */
class RegisteredDistricts(private val codes: Set<String>) {
    fun accepts(districtCode: String): Boolean = districtCode in codes
}
