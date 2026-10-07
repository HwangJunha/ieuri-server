package org.around.ieuri.tourism.adapter.client.kto.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
