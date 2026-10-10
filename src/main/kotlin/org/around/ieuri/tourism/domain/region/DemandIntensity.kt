package org.around.ieuri.tourism.domain.region

import java.math.BigDecimal
import java.time.LocalDate

/** 시군구의 월별 체류·소비 강도 지표 하나(SPEC-0015 R7, KTO). [District.demandIntensity]로만 만든다. [baseMonth]는 그 달 1일이다. */
data class DemandIntensity(
    val districtCode: String,
    val baseMonth: LocalDate,
    val indicatorCode: String,
    val indicatorValue: BigDecimal,
)
