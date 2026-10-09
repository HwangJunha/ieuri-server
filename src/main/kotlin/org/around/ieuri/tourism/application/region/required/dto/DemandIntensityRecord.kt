package org.around.ieuri.tourism.application.region.required.dto

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 월 단위 수요 강도(체류 · 소비 공용). 자연키는 (시군구 코드, 기준월, 지표 코드). [baseMonth]는 해당 월 1일이다.
 * - 값은 소수로 오므로 BigDecimal이다. 정수로 좁히면 반올림된다(SPEC-0015).
 */
data class DemandIntensityRecord(
    val districtCode: String,
    val baseMonth: LocalDate,
    val indicatorCode: String,
    val indicatorValue: BigDecimal,
)
