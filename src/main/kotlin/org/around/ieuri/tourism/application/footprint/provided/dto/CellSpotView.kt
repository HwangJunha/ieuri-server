package org.around.ieuri.tourism.application.footprint.provided.dto

import org.around.ieuri.tourism.domain.shared.Coordinates

/** 칸에 속한 장소 1건. [likeCount]는 정렬 근거이며 활성 언어 기준이다. */
data class CellSpotView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val categoryCode: String,
    val coordinates: Coordinates,
    val likeCount: Long,
)
