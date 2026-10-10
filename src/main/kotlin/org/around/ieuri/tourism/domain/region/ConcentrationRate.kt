package org.around.ieuri.tourism.domain.region

import java.math.BigDecimal
import java.time.LocalDate

/** 시군구 관광지의 날짜별 예상 집중률(SPEC-0015 R7, KTO). [District.concentrationRate]로만 만든다. */
data class ConcentrationRate(
    val districtCode: String,
    val touristSpotName: String,
    val targetDate: LocalDate,
    val concentrationRate: BigDecimal,
)
