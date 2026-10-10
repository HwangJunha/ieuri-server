package org.around.ieuri.tourism.adapter.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.domain.congestion.CommercialActivityLevel
import org.around.ieuri.tourism.domain.congestion.CommercialStatus
import java.time.LocalDateTime
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedCommercial(
    val level: CommercialActivityLevel = CommercialActivityLevel.NORMAL,
    val isDefaulted: Boolean = false,
    val observedAt: String = "",
) {
    fun toDomain() = CommercialStatus(level, isDefaulted, LocalDateTime.parse(observedAt))

    companion object {
        fun from(v: CommercialStatus) = CachedCommercial(v.level, v.isDefaulted, v.observedAt.toString())
    }
}
