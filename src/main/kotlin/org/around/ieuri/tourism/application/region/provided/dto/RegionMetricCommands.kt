package org.around.ieuri.tourism.application.region.provided.dto

import java.math.BigDecimal
import java.time.LocalDate

/*
 * 한국관광공사 관광통계 4종의 적재 입력(SPEC-0015).
 * - 값은 소수로 오므로 [BigDecimal]이다. 정수로 좁히면 반올림된다.
 */

/** 시군구 일별 방문자수. 자연키는 (시군구 코드, 기준일, 관광객 구분). */
data class SyncVisitorCountCommand(
    val districtCode: String,
    val baseDate: LocalDate,
    val visitorType: String,
    val touristCount: BigDecimal,
)

/** 관광지 집중률 예측. 자연키는 (시군구 코드, 관광지명, 대상일). [targetDate]는 관측일이 아니라 예측 대상일이다. */
data class SyncConcentrationRateCommand(
    val districtCode: String,
    val touristSpotName: String,
    val targetDate: LocalDate,
    val concentrationRate: BigDecimal,
)

/** 월 단위 수요 강도(체류 · 소비 공용). 자연키는 (시군구 코드, 기준월, 지표 코드). [baseMonth]는 해당 월 1일이다. */
data class SyncDemandIntensityCommand(
    val districtCode: String,
    val baseMonth: LocalDate,
    val indicatorCode: String,
    val indicatorValue: BigDecimal,
)
