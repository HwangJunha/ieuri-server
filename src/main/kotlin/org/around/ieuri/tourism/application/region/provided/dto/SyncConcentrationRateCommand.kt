package org.around.ieuri.tourism.application.region.provided.dto

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 관광지 집중률 예측. 자연키는 (시군구 코드, 관광지명, 대상일). [targetDate]는 관측일이 아니라 예측 대상일이다.
 * - 값은 소수로 오므로 BigDecimal이다. 정수로 좁히면 반올림된다(SPEC-0015).
 */
data class SyncConcentrationRateCommand(
    val districtCode: String,
    val touristSpotName: String,
    val targetDate: LocalDate,
    val concentrationRate: BigDecimal,
)
