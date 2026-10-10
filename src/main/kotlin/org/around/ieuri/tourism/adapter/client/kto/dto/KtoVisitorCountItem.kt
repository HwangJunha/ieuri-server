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
