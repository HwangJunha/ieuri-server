package org.around.ieuri.tourism.application.command

import java.math.BigDecimal
import java.time.LocalDate

/*
 * 한국관광공사 관광통계 4종의 적재 입력(SPEC-0015 R4~R6).
 *
 * **업스트림 필드명이 여기까지 오지 않는다** — 세 가지 시군구 코드 표기(`signguCode` ·
 * `signguCd` · `sigunguCd`)는 어댑터의 응답 DTO에서 끝나고, 여기서는 [districtCode] 하나다
 * (설계 §8 · POL-033).
 *
 * 값이 전부 [BigDecimal]인 것은 업스트림이 소수 문자열을 주기 때문이다(`"176473.5"`) —
 * 정수로 좁히면 값이 조용히 반올림된다.
 */

/** 시군구 일별 방문자수. 자연키는 (시군구 코드, 기준일, 관광객 구분). */
data class SyncVisitorCountCommand(
    val districtCode: String,
    val baseDate: LocalDate,
    val visitorType: String,
    val touristCount: BigDecimal,
)

/**
 * 관광지 집중률 예측. 자연키는 (시군구 코드, 관광지명, 대상일).
 *
 * [targetDate]는 **예측 대상일**이지 관측일이 아니다. [touristSpotName]이 키에 들어가는 것은
 * 이 값이 시군구가 아니라 개별 관광지 단위이기 때문이다.
 */
data class SyncConcentrationRateCommand(
    val districtCode: String,
    val touristSpotName: String,
    val targetDate: LocalDate,
    val concentrationRate: BigDecimal,
)

/**
 * 월 단위 수요 강도(체류 · 소비 공용). 자연키는 (시군구 코드, 기준월, 지표 코드).
 *
 * [baseMonth]는 **해당 월 1일로 정규화된 날짜다**(V44 주석) — `"202601"` → `2026-01-01`.
 * 일 단위 테이블의 기준일과 타입이 같아져 기간 필터가 같은 형태로 쓰인다.
 */
data class SyncDemandIntensityCommand(
    val districtCode: String,
    val baseMonth: LocalDate,
    val indicatorCode: String,
    val indicatorValue: BigDecimal,
)
