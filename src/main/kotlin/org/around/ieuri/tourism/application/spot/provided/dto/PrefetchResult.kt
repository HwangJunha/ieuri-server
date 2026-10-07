package org.around.ieuri.tourism.application.spot.provided.dto

import org.around.ieuri.tourism.application.spot.provided.dto.SpotDetailPart

/** 한 묶음의 처리 결과. 소진 판정은 성공 건수가 아니라 [processed]가 0인지로 한다. */
data class PrefetchResult(
    val processed: Int,
    val failed: Int,
    val lastSpotId: Long,
)
