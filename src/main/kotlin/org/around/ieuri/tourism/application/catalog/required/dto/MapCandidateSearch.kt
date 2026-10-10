package org.around.ieuri.tourism.application.catalog.required.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds

/** bbox 추천 후보 조회 조건(SPEC-0036). [limit]은 후보 상한 그대로다(`+1` 아님). */
data class MapCandidateSearch(
    val language: DomainLocale,
    val bounds: MapBounds,
    val categories: CategorySelection,
    /** 제외할 카탈로그 id(반응한 장소 + 이미 표시한 장소). 개수 상한 없음. */
    val excludedSpotIds: Set<Long>,
    val limit: Int,
)
