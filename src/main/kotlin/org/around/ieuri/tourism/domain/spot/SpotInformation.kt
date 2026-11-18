package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.category.SpotCategory

/** 원본에 딸린, [category]로 읽은 이용안내 전량([Spot.inform]으로만 만든다). 순서가 곧 표시 순서다. */
data class SpotInformation(
    val spotId: Long,
    val category: SpotCategory,
    val items: List<SpotInfoItem>,
)
