package org.around.ieuri.tourism.infrastructure.adapter.out.client.climateindex.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * 관광기후지수 응답 봉투. **어댑터 내부 전용** — 업스트림 필드명을 그대로 담으며 어댑터 밖으로
 * 나가지 않는다(POL-033 · 설계 §8).
 *
 * **성공 코드가 `"00"`이다**(실측 2026-08-11) — 데이터포털 공통 `"0000"`이 아니다. 오류도
 * HTTP 200으로 오므로 이 코드를 보지 않으면 실패가 성공으로 통과한다.
 *
 * 인증 실패 등에서는 이 봉투가 아니라 `OpenAPI_ServiceResponse` 형태가 오며, 그때는 header가
 * 비어 [isSuccess]가 참이 되어 버린다 — 그래서 [hasHeader]로 봉투 자체를 구분한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClimateIndexResponse(
    val response: Envelope = Envelope(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Envelope(
        val header: Header? = null,
        val body: Body = Body(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Header(
        val resultCode: String = "",
        val resultMsg: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Body(
        /**
         * 결과가 없으면 객체가 아니라 빈 문자열 `""`이 올 수 있다(관광통계 4종에서 실측된 동작).
         * `ACCEPT_EMPTY_STRING_AS_NULL_OBJECT`가 그 빈 문자열을 null로 흡수하고 [items]가
         * null을 빈 목록으로 바꾼다 — **부재가 장애로 둔갑하지 않아야 한다**(R8).
         */
        val items: Items? = null,
        val numOfRows: Int = 0,
        val pageNo: Int = 0,
        val totalCount: Int = 0,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Items(
        val item: List<ClimateIndexItem> = emptyList(),
    )

    /** 정상 응답 봉투인지. 오류 봉투(`OpenAPI_ServiceResponse`)에는 이 header가 없다. */
    val hasHeader: Boolean get() = response.header != null

    /** 응답이 성공인지. 이 서비스의 성공 코드는 `"00"`이다. */
    val isSuccess: Boolean get() = resultCode in SUCCESS_CODES

    /** 호출 한도 초과인지(R8 — UPSTREAM_RATE_LIMITED로 변환). */
    val isRateLimited: Boolean get() = resultCode in RATE_LIMIT_CODES

    val resultCode: String get() = response.header?.resultCode ?: ""
    val resultMessage: String get() = response.header?.resultMsg ?: ""
    val items: List<ClimateIndexItem> get() = response.body.items?.item ?: emptyList()
    val totalCount: Int get() = response.body.totalCount

    companion object {
        /** `"00"`이 이 서비스의 성공 코드다. `"0000"`도 인정해 둔다 — 표기가 바뀌어도 깨지지 않는다. */
        private val SUCCESS_CODES = setOf("00", "0000", "0")

        /** 공공데이터포털 공통 한도 초과(`LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR`). */
        private val RATE_LIMIT_CODES = setOf("22", "0022")
    }
}

/**
 * 관광기후지수 한 행.
 *
 * **모든 필드가 nullable이다** — 필드 하나가 빠졌을 때 페이지 전체의 역직렬화가 깨지는 것보다
 * 그 행만 드랍하는 편이 낫다.
 *
 * [kmaTci]는 **JSON 숫자다**(관광통계 4종이 소수 *문자열*을 주는 것과 다르다).
 * [tciGrade]만 대문자 스네이크(`TCI_GRADE`)라 [JsonProperty]로 이름을 맞춘다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClimateIndexItem(
    /** 예보 대상 시각 `"yyyy-MM-dd HH:mm"`. 날짜 부분만 쓴다. */
    val tm: String? = null,
    /** 시군구 아이디 10자리 = **행정표준코드 5자리 + `"00000"`**. 앞 5자리만 쓴다. */
    val cityAreaId: String? = null,
    val totalCityName: String? = null,
    val doName: String? = null,
    val cityName: String? = null,
    /** 관광기후지수 원값. 실측 범위 -0.66 ~ 0.52. */
    val kmaTci: java.math.BigDecimal? = null,
    /** 등급('매우좋음'·'좋음'·'보통'·'나쁨'). 이 필드만 대문자 스네이크로 온다. */
    @param:JsonProperty("TCI_GRADE")
    val tciGrade: String? = null,
)
