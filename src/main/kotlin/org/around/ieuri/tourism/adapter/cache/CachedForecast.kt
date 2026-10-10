package org.around.ieuri.tourism.adapter.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.domain.congestion.CongestionForecast
import org.around.ieuri.tourism.domain.congestion.CongestionLevel
import java.time.LocalDateTime
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedForecast(
    val forecastTime: String = "",
    val level: CongestionLevel = CongestionLevel.NORMAL,
    val populationMin: Int = 0,
    val populationMax: Int = 0,
) {
    fun toDomain() = CongestionForecast(LocalDateTime.parse(forecastTime), level, populationMin, populationMax)

    companion object {
        fun from(v: CongestionForecast) = CachedForecast(v.forecastTime.toString(), v.level, v.populationMin, v.populationMax)
    }
}
