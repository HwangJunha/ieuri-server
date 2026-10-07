package org.around.ieuri.tourism.application.catalog.required.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 반경 조회 입력.
 * - [excludedSpotIds]는 DB 쿼리에서 제외해야 한다. 조회 후 거르면 페이지가 size보다 작아진다.
 */
data class NearbySpotSearch(
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    /** 비어 있으면 전체, 여럿이면 OR. */
    val categories: CategorySelection,
    val excludedSpotIds: Set<Long>,
    val limit: Int,
    val offset: Int,
)
