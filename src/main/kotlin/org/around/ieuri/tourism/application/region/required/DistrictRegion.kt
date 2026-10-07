package org.around.ieuri.tourism.application.region.required

/**
 * 시군구 코드(5자리)와 그 시도 코드(2자리). 둘 다 문자열이라 뒤바뀌기 쉽다.
 * - 지표 배치의 순회 대상이자, 추천에서 origin 좌표가 들어앉은 시군구다(없으면 추천은 빈 목록).
 * - 집중률 요청은 둘 다 필수다. 하나만 보내면 `NO_MANDATORY_REQUEST_PARAMETERS_ERROR1`이 온다.
 */
data class DistrictRegion(
    val code: String,
    val provinceCode: String,
)
