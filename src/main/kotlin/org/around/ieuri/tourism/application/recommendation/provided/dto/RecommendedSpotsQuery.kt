package org.around.ieuri.tourism.application.recommendation.provided.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection

/** recommendedSpots 입력(SPEC-0035). 페이지 대신 묶음 크기 [first]만 받는다. */
data class RecommendedSpotsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(`spots`와 같은 규약). */
    val categories: CategorySelection,
    val first: Int,
)
