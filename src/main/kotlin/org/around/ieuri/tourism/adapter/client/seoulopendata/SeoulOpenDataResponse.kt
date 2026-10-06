package org.around.ieuri.tourism.adapter.client.seoulopendata

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * 서울시 실시간 도시데이터(`seoulOpenData`) 응답 DTO(SPEC-0014 §3). 쓰는 세 블록만 선언한다.
 * - 필드 타입이 일관되지 않아 숫자 자리도 `String`으로 받고 매퍼가 관대하게 파싱한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SeoulOpenDataResponse(
    @get:com.fasterxml.jackson.annotation.JsonProperty("RESULT")
    val result: Result? = null,
    @get:com.fasterxml.jackson.annotation.JsonProperty("CITYDATA")
    val cityData: CityData? = null,
) {
    /**
     * 업스트림 처리 결과. 정상은 `INFO-000`이며 그 외는 전부 실패다(HTTP 상태는 오류여도 200).
     * - 인증키가 틀리면 XML로 응답이 와서 역직렬화에서 먼저 깨진다 — 호출부의 catch가 흡수한다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Result(
        @get:com.fasterxml.jackson.annotation.JsonProperty("RESULT.CODE")
        val code: String? = null,
        @get:com.fasterxml.jackson.annotation.JsonProperty("RESULT.MESSAGE")
        val message: String? = null,
    ) {
        val isSuccess: Boolean get() = code == SUCCESS_CODE

        companion object {
            const val SUCCESS_CODE = "INFO-000"
        }
    }

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
     * 상권 블록. `CMRCL_TIME`은 `"20260810 0600"` 형식으로 인구 블록과 다르다.
     * - `AREA_SH_PAYMENT_CNT`가 없으면 `보통`이 기준선 없는 자리표시자일 가능성이 높다.
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

    /** 문화행사. `EVENT_X`가 경도, `EVENT_Y`가 위도다(spec §7.7). */
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
