package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourstats.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * KTO 빅데이터 4종의 응답 행. **여기가 세 가지 시군구 코드 표기를 아는 유일한 곳이다**
 * (spec §4.5 · 설계 §8) — `signguCode`(방문자수) · `signguCd`(집중률 · 강도 2종).
 * 매퍼가 도메인 명령으로 옮기는 순간 이름은 하나가 된다.
 *
 * **모든 필드가 nullable이다.** 응답 스키마가 문서로 확정되지 않아, 필드 하나가 빠졌을 때
 * 페이지 전체의 역직렬화가 깨지는 것보다 그 행만 드랍하는 편이 낫다(R9와 같은 취급).
 *
 * **숫자 필드는 String이다.** 업스트림이 소수 문자열을 준다(`"176473.5"`) — 정수로 받으면
 * 역직렬화가 실패하거나 값이 조용히 반올림된다(spec §4.7 · R4).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourStatsVisitorCountItem(
    /** 시군구 코드. **이 API만 `signguCode`다.** */
    val signguCode: String? = null,
    val signguNm: String? = null,
    /** 기준일 `yyyyMMdd`. */
    val baseYmd: String? = null,
    /** 관광객 구분(현지인 · 외지인 · 외국인 등). 값 목록을 코드로 고정하지 않는다. */
    val touDivNm: String? = null,
    val touDivCd: String? = null,
    /** 방문자 수. **소수 문자열이다** — BigDecimal로 파싱한다. */
    val touNum: String? = null,
)

/**
 * 관광지 집중률 행. 시군구가 아니라 **관광지 단위** 값이라 지역 점수로 쓰려면 집계가 필요하다
 * (설계 §3.2). [cnctrRate]는 가장 붐비는 시기를 100으로 둔 상대값이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourStatsConcentrationRateItem(
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
 * 체류 강도 · 소비 강도 행.
 *
 * **두 오퍼레이션의 지표 필드명이 서로 다르다**(실측 2026-08-11) — 체류는 `tarSjrnDsIx*`,
 * 소비는 `tarExpDsIx*`다. 한 타입으로 받되 양쪽 이름을 모두 두고 [indicatorCode] ·
 * [indicatorValue]로 합류시킨다. 응답에 없는 쪽은 null로 남는다.
 *
 * [baseYm]은 `yyyyMM`이고 적재 시 해당 월 1일 `date`로 정규화한다(V44 주석).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TourStatsDemandIntensityItem(
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
