package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection

/** 인기 spot 조회 입력. 좋아요 최소 기준은 정책이 정한다. */
data class PopularSpotsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(SPEC-0020 R5). */
    val categories: CategorySelection,
    val first: Int,
    val after: String?,
)
