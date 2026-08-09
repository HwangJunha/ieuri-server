package org.around.ieuri.tourism.infrastructure.adapter.out.citydata

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * 서울시 실시간 도시데이터(`citydata`) 응답 DTO(SPEC-0014 §3).
 *
 * **필드 타입이 일관되지 않다** — 인구 관련 값은 문자열로 오는데(`"3000"`)
 * `AREA_SH_PAYMENT_AMT_MIN`은 숫자로 온다. 그래서 숫자 자리도 `String`으로 받아
 * 매퍼에서 관대하게 파싱한다. Jackson이 숫자를 문자열로 읽어 주므로 양쪽 다 통과한다.
 *
 * 이 응답에는 도로·주차·지하철·날씨 등 훨씬 많은 블록이 있으나 본 spec이 쓰는 셋만
 * 선언한다 — `ignoreUnknown`이 나머지를 흡수한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SeoulCitydataResponse(
    @get:com.fasterxml.jackson.annotation.JsonProperty("CITYDATA")
    val cityData: CityData? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CityData(
        @get:com.fasterxml.jackson.annotation.JsonProperty("AREA_CD")
        val areaCode: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("LIVE_PPLTN_STTS")
        val population: List<Population> = emptyList(),
        @get:com.fasterxml.jackson.annotation.JsonProperty("LIVE_CMRCL_STTS")
        val commercial: Commercial? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("EVENT_STTS")
        val events: List<Event> = emptyList(),
    )

    /** 인구 블록. `PPLTN_TIME`은 `"2026-08-10 05:45"` 형식이다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Population(
        @get:com.fasterxml.jackson.annotation.JsonProperty("AREA_CONGEST_LVL")
        val congestionLevel: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("AREA_PPLTN_MIN")
        val populationMin: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("AREA_PPLTN_MAX")
        val populationMax: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("PPLTN_TIME")
        val observedAt: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("FCST_YN")
        val forecastAvailable: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("FCST_PPLTN")
        val forecast: List<Forecast> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Forecast(
        @get:com.fasterxml.jackson.annotation.JsonProperty("FCST_TIME")
        val forecastTime: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("FCST_CONGEST_LVL")
        val congestionLevel: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("FCST_PPLTN_MIN")
        val populationMin: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("FCST_PPLTN_MAX")
        val populationMax: String? = null,
    )

    /**
     * 상권 블록. **`CMRCL_TIME`은 `"20260810 0600"` 형식으로 인구 블록과 다르다**
     * (2026-08-10 실측). 매퍼가 두 형식을 모두 읽는다.
     *
     * `AREA_SH_PAYMENT_CNT`는 실측 기준선 유무의 단서다 — 결제 건수가 없으면 업스트림이
     * `보통`을 고정 반환한 자리표시자일 가능성이 높다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Commercial(
        @get:com.fasterxml.jackson.annotation.JsonProperty("AREA_CMRCL_LVL")
        val level: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("AREA_SH_PAYMENT_CNT")
        val paymentCount: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("CMRCL_TIME")
        val observedAt: String? = null,
    )

    /**
     * 문화행사. **`EVENT_X`가 경도, `EVENT_Y`가 위도로 이름이 직관과 반대다**(spec §7.7).
     *
     * `THUMBNAIL`·`PAY_YN`·`EVENT_ETC_DETAIL`은 의도적으로 선언하지 않는다 — 계약에
     * 넣지 않기로 했다(spec §7.9).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Event(
        @get:com.fasterxml.jackson.annotation.JsonProperty("EVENT_NM")
        val title: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("EVENT_PERIOD")
        val period: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("EVENT_PLACE")
        val place: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("EVENT_X")
        val longitude: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("EVENT_Y")
        val latitude: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("URL")
        val url: String? = null,
    )
}
