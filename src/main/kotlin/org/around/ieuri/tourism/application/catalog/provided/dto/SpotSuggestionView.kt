package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.tourism.domain.category.SpotCategory

/** 자동완성 항목 1건. 제목 접두 매칭 결과다. */
data class SpotSuggestionView(
    val id: Long,
    val title: String,
    val category: SpotCategory,
)
