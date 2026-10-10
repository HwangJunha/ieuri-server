package org.around.ieuri.tourism.application.catalog.required.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 검색 조건(오프셋 방식). [limit]·[offset]은 호출자가 이미 상한을 적용한 값이다. */
data class SpotSearchQuery(
    val text: String,
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체. 값이 여럿이면 IN으로 거른다(OR). */
    val categories: CategorySelection,
    val limit: Int,
    val offset: Int,
)
