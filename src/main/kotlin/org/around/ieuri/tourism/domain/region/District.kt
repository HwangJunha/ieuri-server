package org.around.ieuri.tourism.domain.region

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 시군구(SPEC-0015). 지역 지표와 대표 위치의 주인이다.
 * - 지표(방문자 수·집중률·체류·소비 강도)는 이 루트의 팩토리로만 만든다 — 등록되지 않은 시군구 코드의 지표는 생기지 않는다(R9).
 * - 지표는 날마다 쌓이는 자식이라 루트가 들고 있지 않는다. 자식은 [code]를 가진다.
 */
data class District(
    val code: String,
    val hasRepresentative: Boolean,
) {
    fun visitorCount(baseDate: LocalDate, visitorType: String, touristCount: BigDecimal) = VisitorCount(code, baseDate, visitorType, touristCount)

    fun concentrationRate(touristSpotName: String, targetDate: LocalDate, rate: BigDecimal) = ConcentrationRate(code, touristSpotName, targetDate, rate)

    fun demandIntensity(baseMonth: LocalDate, indicatorCode: String, indicatorValue: BigDecimal) = DemandIntensity(code, baseMonth, indicatorCode, indicatorValue)

    /** 이번에 고른 대표 위치. 고른 것이 없으면 있던 것을 비우고, 원래 없었으면 그대로다. */
    fun relocateRepresentative(choice: RepresentativeLocation.Choice?): RepresentativeRelocation = when {
        choice != null -> RepresentativeRelocation.Assign(choice)
        hasRepresentative -> RepresentativeRelocation.Clear
        else -> RepresentativeRelocation.Unchanged
    }
}
