package org.around.ieuri.tourism.domain.recommendation

import org.around.ieuri.tourism.domain.category.SpotCategory

/** 재배열 대상 1건. */
data class RankedSpot(
    val id: Long,
    val category: SpotCategory,
    val score: Double,
)
