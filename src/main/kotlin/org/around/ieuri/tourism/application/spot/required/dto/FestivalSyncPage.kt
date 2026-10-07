package org.around.ieuri.tourism.application.spot.required.dto

import org.around.ieuri.tourism.domain.spot.SpotFestival

/** 수집된 한 페이지. [droppedCount]는 날짜 해석 실패 등으로 드랍한 관측용 건수다. */
data class FestivalSyncPage(
    val records: List<SpotFestival>,
    val totalCount: Int,
    val droppedCount: Int,
)
