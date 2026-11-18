package org.around.ieuri.tourism.adapter.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.application.congestion.required.dto.CachedSeoulOpenData
import org.around.ieuri.tourism.application.congestion.required.dto.SeoulOpenDataSnapshot
import java.time.Instant
/**
 * 캐시 직렬화 DTO. 도메인 타입과 분리해 도메인이 바뀌어도 옛 항목이 역직렬화된다.
 * - 모든 필드에 기본값이 있어 필드가 추가돼도 옛 항목이 읽힌다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedSeoulOpenDataPayload(
    val areaCode: String = "",
    val storedAtEpochSecond: Long = 0,
    val congestion: CachedSnapshot? = null,
    val forecast: List<CachedForecast>? = null,
    val commercial: CachedCommercial? = null,
    val events: List<CachedEvent> = emptyList(),
) {
    fun toDomain() = CachedSeoulOpenData(
        snapshot = SeoulOpenDataSnapshot(
            areaCode = areaCode,
            congestion = congestion?.toDomain(),
            forecast = forecast?.map(CachedForecast::toDomain),
            commercial = commercial?.toDomain(),
            events = events.map(CachedEvent::toDomain),
        ),
        storedAt = Instant.ofEpochSecond(storedAtEpochSecond),
    )

    companion object {
        fun from(snapshot: SeoulOpenDataSnapshot, storedAt: Instant) = CachedSeoulOpenDataPayload(
            areaCode = snapshot.areaCode,
            storedAtEpochSecond = storedAt.epochSecond,
            congestion = snapshot.congestion?.let(CachedSnapshot::from),
            forecast = snapshot.forecast?.map(CachedForecast::from),
            commercial = snapshot.commercial?.let(CachedCommercial::from),
            events = snapshot.events.map(CachedEvent::from),
        )
    }
}
