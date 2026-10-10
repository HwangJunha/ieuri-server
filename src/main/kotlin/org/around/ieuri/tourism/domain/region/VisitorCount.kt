package org.around.ieuri.tourism.domain.region

import java.math.BigDecimal
import java.time.LocalDate

/** 시군구의 일별 방문자 수(SPEC-0015 R7, KTO). [District.visitorCount]로만 만든다. */
data class VisitorCount(
    val districtCode: String,
    val baseDate: LocalDate,
    val visitorType: String,
    val touristCount: BigDecimal,
)
