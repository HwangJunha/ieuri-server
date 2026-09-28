package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourstats.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * KTO 빅데이터 4종의 공통 응답 봉투(어댑터 내부 전용). 미정의 필드는 무시한다.
 * - KTO는 오류도 HTTP 200으로 준다 — 실패 여부는 [Header.resultCode]에만 나타난다.
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
        /** 결과가 없으면 빈 문자열 `""`이 온다 — null로 흡수되고 [TourStatsResponse.items]가 빈 목록으로 바꾼다(R8). */
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
        /** 성공 코드. 빈 문자열도 성공으로 본다 — header 자체를 주지 않는 응답이 있다. */
        private val SUCCESS_CODES = setOf("0000", "00", "0")

        /** 호출 한도 초과 코드(`LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR`와 4자리 표기). */
        private val RATE_LIMIT_CODES = setOf("22", "0022")
    }
}
