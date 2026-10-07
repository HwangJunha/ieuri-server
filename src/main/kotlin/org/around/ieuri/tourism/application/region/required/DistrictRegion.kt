package org.around.ieuri.tourism.application.region.required

/**
 * 시군구 코드와 그 시도 코드. 둘 다 문자열이라 뒤바뀌기 쉽다.
 * - 지표 배치의 순회 대상이자, 추천에서 origin 좌표가 들어앉은 시군구다(없으면 추천은 빈 목록).
 */
data class DistrictRegion(
    val code: String,
    val provinceCode: String,
)
