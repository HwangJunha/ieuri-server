package org.around.ieuri.tourism.application.catalog.required.dto

import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.DomainLocale
import java.time.LocalDate

/**
 * 진행 중 축제 조회 조건. [asOf]는 서비스가 시계에서 읽어 넘긴 '오늘'이다.
 * - 경계를 포함한다: `event_start_date <= asOf AND event_end_date >= asOf`.
 */
data class OngoingFestivalSearch(
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    val asOf: LocalDate,
    val limit: Int,
    /** 앞 페이지의 endCursor. 읽지 못하면 INVALID_CURSOR다. */
    val after: String?,
)
