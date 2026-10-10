package org.around.ieuri.tourism.application.catalog.required.dto

import org.around.ieuri.tourism.domain.category.SpotCategory

/** 추천 후보 1건. [distanceMeters]는 사용자 위치가 아니라 bbox 중심점까지의 구면 거리다. */
data class MapCandidateView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val category: SpotCategory,
    val lng: Double,
    val lat: Double,
    val distanceMeters: Double,
)
