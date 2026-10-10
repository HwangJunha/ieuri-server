package org.around.ieuri.tourism.application.catalog.required.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds

/**
 * bbox 마커 조회 조건. [limit]은 상한 + 1로 넘어온다.
 * - 언어 필터를 빼면 같은 마커가 언어 수만큼 나온다.
 */
data class MapViewportSearch(
    val language: DomainLocale,
    val bounds: MapBounds,
    /** 비어 있으면 전체, 여럿이면 OR. 상한보다 먼저 적용된다. */
    val categories: CategorySelection,
    val limit: Int,
)
