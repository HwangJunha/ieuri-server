package org.around.ieuri.tourism.application.catalog.required.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 인기 spot 조회 조건. [minLikeCount]는 집계 후 적용하는 하한이다. */
data class PopularSpotSearch(
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체. 값이 여럿이면 IN으로 거른다(OR). */
    val categories: CategorySelection,
    val minLikeCount: Int,
    val limit: Int,
    /** 앞 페이지의 endCursor. 읽지 못하면 INVALID_CURSOR다. */
    val after: String?,
)
