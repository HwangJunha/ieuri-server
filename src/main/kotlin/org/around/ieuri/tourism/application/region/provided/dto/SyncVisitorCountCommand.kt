package org.around.ieuri.tourism.application.region.provided.dto

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 시군구 일별 방문자수. 자연키는 (시군구 코드, 기준일, 관광객 구분).
 * - 값은 소수로 오므로 BigDecimal이다. 정수로 좁히면 반올림된다(SPEC-0015).
 */
data class SyncVisitorCountCommand(
    val districtCode: String,
    val baseDate: LocalDate,
    val visitorType: String,
    val touristCount: BigDecimal,
)
