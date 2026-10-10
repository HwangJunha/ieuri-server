package org.around.ieuri.tourism.application.catalog.provided.dto

import java.time.LocalDate

/** 진행 중 축제 1건. 날짜에는 시각 성분이 없다. */
data class FestivalView(
    val spot: SpotView,
    val eventStartDate: LocalDate,
    val eventEndDate: LocalDate,
)
