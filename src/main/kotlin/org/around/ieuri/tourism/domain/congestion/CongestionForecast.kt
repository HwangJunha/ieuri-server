package org.around.ieuri.tourism.domain.congestion

import java.time.LocalDateTime

/** 12개 시간대 예보 중 1건. */
data class CongestionForecast(
    val forecastTime: LocalDateTime,
    val level: CongestionLevel,
    val populationMin: Int,
    val populationMax: Int,
)
