package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType

/** 주변·검색·추천 목록의 카드 1장. [distanceMeters]는 요청 좌표로부터의 구면 거리(미터)다. */
data class SpotView(
    val id: Long,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val distanceMeters: Double,
    val category: SpotCategory,
    val copyright: CopyrightType,
    val mapX: Double,
    val mapY: Double,
) {
    val attributionText: String get() = copyright.attributionText
}
