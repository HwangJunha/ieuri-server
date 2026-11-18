package org.around.ieuri.tourism.adapter.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedSpotInfoItem(
    val name: String = "",
    val text: String = "",
) {
    fun toDomain(): SpotInfoItem = SpotInfoItem(name = name, text = text)
}
