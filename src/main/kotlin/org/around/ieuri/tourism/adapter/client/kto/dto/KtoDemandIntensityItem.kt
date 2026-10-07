package org.around.ieuri.tourism.adapter.client.kto.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
/**
 * 체류 강도 · 소비 강도 행. 두 오퍼레이션의 지표 필드명이 달라(`tarSjrnDsIx*`·`tarExpDsIx*`) 양쪽을 두고 합류시킨다.
 * - [baseYm]은 `yyyyMM`이며 적재 시 그 달 1일로 정규화한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KtoDemandIntensityItem(
    val signguCd: String? = null,
    val signguNm: String? = null,
    val areaCd: String? = null,
    /** 기준월 `yyyyMM`. */
    val baseYm: String? = null,
    /** 체류 강도 지표 코드·값(`21` = 관광체류강도). */
    val tarSjrnDsIxCd: String? = null,
    val tarSjrnDsIxNm: String? = null,
    val tarSjrnDsIxVal: String? = null,
    /** 소비 강도 지표 코드·값(`22` = 관광소비강도). */
    val tarExpDsIxCd: String? = null,
    val tarExpDsIxNm: String? = null,
    val tarExpDsIxVal: String? = null,
) {
    /** 오퍼레이션마다 다른 지표 필드를 하나로 합류시킨다. */
    val indicatorCode: String? get() = tarSjrnDsIxCd ?: tarExpDsIxCd

    /** 지표 값. 소수 문자열이다. */
    val indicatorValue: String? get() = tarSjrnDsIxVal ?: tarExpDsIxVal
}
