package org.around.ieuri.tourism.adapter.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.domain.congestion.CongestionLevel
import org.around.ieuri.tourism.domain.congestion.CongestionSnapshot
import java.time.LocalDateTime
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedSnapshot(
    val level: CongestionLevel = CongestionLevel.NORMAL,
    val populationMin: Int = 0,
    val populationMax: Int = 0,
    val observedAt: String = "",
) {
    fun toDomain() = CongestionSnapshot(level, populationMin, populationMax, LocalDateTime.parse(observedAt))

    companion object {
        fun from(v: CongestionSnapshot) = CachedSnapshot(v.level, v.populationMin, v.populationMax, v.observedAt.toString())
    }
}
