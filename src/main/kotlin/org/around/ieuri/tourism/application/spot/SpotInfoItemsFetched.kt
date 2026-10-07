package org.around.ieuri.tourism.application.spot

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.spot.SpotInfoItem

data class SpotInfoItemsFetched(
    val spotId: Long,
    val category: SpotCategory,
    val items: List<SpotInfoItem>,
)
