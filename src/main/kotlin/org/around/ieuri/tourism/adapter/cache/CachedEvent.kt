package org.around.ieuri.tourism.adapter.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.application.congestion.provided.dto.CulturalEventView
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedEvent(
    val id: String = "",
    val title: String = "",
    val place: String = "",
    val period: String = "",
    val lng: Double = 0.0,
    val lat: Double = 0.0,
    val url: String? = null,
) {
    fun toDomain() = CulturalEventView(id, title, place, period, lng, lat, url)

    companion object {
        fun from(v: CulturalEventView) = CachedEvent(v.id, v.title, v.place, v.period, v.lng, v.lat, v.url)
    }
}
