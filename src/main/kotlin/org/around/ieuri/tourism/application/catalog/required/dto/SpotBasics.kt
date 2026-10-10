package org.around.ieuri.tourism.application.catalog.required.dto

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType

/** `spot` 행이 보유한 상세 표시 값(read DTO). */
data class SpotBasics(
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val category: SpotCategory,
    val copyright: CopyrightType,
    /** 경도. */
    val longitude: Double,
    /** 위도. */
    val latitude: Double,
)
