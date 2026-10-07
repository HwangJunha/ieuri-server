package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.tourism.domain.category.SpotCategory

/** 지도 마커 1개. 마커에 필요한 필드만 담으며 거리는 없다. */
data class MapSpotView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val category: SpotCategory,
    val lng: Double,
    val lat: Double,
)
