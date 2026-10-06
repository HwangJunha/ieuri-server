package org.around.ieuri.tourism.adapter.client.kto.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * KTO 빅데이터 4종의 응답 행. 시군구 코드 표기(`signguCode`·`signguCd`)의 차이를 아는 유일한 곳이다.
 * - 모든 필드가 nullable이다 — 필드가 빠지면 페이지 전체가 아니라 그 행만 드랍한다.
 * - 숫자 필드는 소수 문자열(`"176473.5"`)이라 String이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KtoVisitorCountItem(
    /** 시군구 코드. 이 API만 `signguCode`다. */
    val signguCode: String? = null,
    val signguNm: String? = null,
    /** 기준일 `yyyyMMdd`. */
    val baseYmd: String? = null,
    /** 관광객 구분(현지인 · 외지인 · 외국인 등). 값 목록을 코드로 고정하지 않는다. */
    val touDivNm: String? = null,
    val touDivCd: String? = null,
    /** 방문자 수. 소수 문자열이다. */
    val touNum: String? = null,
)

/**
 * 관광지 집중률 행. 관광지 단위 값이라 지역 점수로 쓰려면 집계가 필요하다.
 * - [cnctrRate]는 가장 붐비는 시기를 100으로 둔 상대값이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KtoConcentrationRateItem(
    /** 시군구 코드. 방문자수의 `signguCode`와 표기가 다르다. */
    val signguCd: String? = null,
    val signguNm: String? = null,
    val areaCd: String? = null,
    /** 관광지명. 이 API만 자연키에 들어간다. spot과 이름으로 매칭하지 않는다. */
    val tAtsNm: String? = null,
    /** 예측 대상일 `yyyyMMdd`. 관측일이 아니라 예측이 향하는 날짜다. */
    val baseYmd: String? = null,
    val cnctrRate: String? = null,
)

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
