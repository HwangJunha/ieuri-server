package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourstats.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * KTO 빅데이터 4종의 공통 응답 봉투. **어댑터 내부 전용** — 업스트림 필드명을 그대로 담으며
 * 어댑터 밖으로 나가지 않는다(POL-033 · 설계 §8).
 *
 * 미정의 필드는 전부 무시한다 — 응답 스키마가 문서로 확정되지 않아, 모르는 필드가 와도
 * 역직렬화가 깨지지 않아야 한다.
 *
 * **[header]의 [Header.resultCode]를 반드시 봐야 한다.** KTO는 오류도 HTTP 200으로 주며
 * 실패 여부는 이 코드에만 나타난다. 성공은 `"0000"`(일부 서비스는 `"00"`)이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourStatsResponse<T>(
    val response: Envelope<T> = Envelope(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Envelope<T>(
        val header: Header = Header(),
        val body: Body<T> = Body(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Header(
        val resultCode: String = "",
        val resultMsg: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Body<T>(
        /**
         * **결과가 없으면 객체가 아니라 빈 문자열 `""`이 온다**(실측 2026-08-11). 고정 타입으로
         * 받으면 결과 없는 날짜마다 역직렬화가 깨져 **부재가 `UPSTREAM_UNAVAILABLE` 장애로
         * 둔갑한다**(R8 위반). `ACCEPT_EMPTY_STRING_AS_NULL_OBJECT`가 그 빈 문자열을 null로
         * 흡수하고, [TourStatsResponse.items]가 null을 빈 목록으로 바꾼다.
         */
        val items: Items<T>? = null,
        val numOfRows: Int = 0,
        val pageNo: Int = 0,
        val totalCount: Int = 0,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Items<T>(
        val item: List<T> = emptyList(),
    )

    /** 응답이 성공인지. 성공 코드가 서비스마다 `"0000"`/`"00"`으로 갈려 둘 다 인정한다. */
    val isSuccess: Boolean
        get() = response.header.resultCode.let { it.isBlank() || it in SUCCESS_CODES }

    /** 호출 한도 초과인지(R8 — UPSTREAM_RATE_LIMITED로 변환). */
    val isRateLimited: Boolean
        get() = response.header.resultCode in RATE_LIMIT_CODES

    val resultCode: String get() = response.header.resultCode
    val resultMessage: String get() = response.header.resultMsg
    val items: List<T> get() = response.body.items?.item ?: emptyList()
    val totalCount: Int get() = response.body.totalCount

    companion object {
        /**
         * 성공 코드. `"0000"`은 데이터포털 공통 규약, `"00"`은 일부 서비스가 쓰는 축약형이다.
         * 빈 문자열도 성공으로 본다 — header 자체를 주지 않는 응답이 있다.
         */
        private val SUCCESS_CODES = setOf("0000", "00", "0")

        /**
         * 호출 한도 초과 코드. `22`는 공공데이터포털 공통 `LIMITED_NUMBER_OF_SERVICE_REQUESTS_
         * EXCEEDS_ERROR`이며, `"0022"`는 같은 값의 4자리 표기다.
         */
        private val RATE_LIMIT_CODES = setOf("22", "0022")
    }
}
