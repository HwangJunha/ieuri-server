package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection

/** spot 검색 입력(SPEC-0013). 오프셋 페이지네이션이며 [page]는 1부터 센다. 언어는 Accept-Language에서 온다. */
data class SearchSpotsQuery(
    val query: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(SPEC-0020 R5). */
    val categories: CategorySelection,
    val page: Int,
    val size: Int,
)
