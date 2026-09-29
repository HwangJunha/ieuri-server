package org.around.ieuri.tourism.infrastructure.adapter.out.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.application.port.out.cache.CachedSeoulOpenData
import org.around.ieuri.tourism.application.port.out.cache.SeoulOpenDataCachePort
import org.around.ieuri.tourism.application.port.out.client.SeoulOpenDataSnapshot
import org.around.ieuri.tourism.application.query.CulturalEventView
import org.around.ieuri.tourism.domain.vo.CommercialActivityLevel
import org.around.ieuri.tourism.domain.vo.CommercialStatus
import org.around.ieuri.tourism.domain.vo.CongestionForecast
import org.around.ieuri.tourism.domain.vo.CongestionLevel
import org.around.ieuri.tourism.domain.vo.CongestionSnapshot
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * 서울 실시간 도시데이터 Valkey 캐시(SPEC-0014 R13·R14). 키는 `ieuri:tourism:seoulOpenData:{areaCd}`다.
 * - 신선도 판정은 값 안의 저장 시각으로 서비스가 한다(R13). TTL은 상한일 뿐이다.
 * - Valkey 장애·역직렬화 실패는 삼킨다.
 */
@Component
class ValkeySeoulOpenDataCache(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : SeoulOpenDataCachePort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun get(areaCode: String): CachedSeoulOpenData? = try {
        redis.opsForValue().get(key(areaCode))?.let {
            objectMapper.readValue(it, CachedSeoulOpenDataPayload::class.java).toDomain()
        }
    } catch (e: Exception) {
        log.warn("서울 실시간 도시데이터 캐시 조회 실패, upstream 폴백: {}", e.javaClass.simpleName)
        null
    }

    override fun put(areaCode: String, snapshot: SeoulOpenDataSnapshot, storedAt: Instant) {
        try {
            val payload = CachedSeoulOpenDataPayload.from(snapshot, storedAt)
            redis.opsForValue().set(key(areaCode), objectMapper.writeValueAsString(payload), TTL)
        } catch (e: Exception) {
            log.warn("서울 실시간 도시데이터 캐시 저장 실패: {}", e.javaClass.simpleName)
        }
    }

    private fun key(areaCode: String) = "$KEY_PREFIX$areaCode"

    private companion object {
        const val KEY_PREFIX = "ieuri:tourism:seoulOpenData:"

        /** 값의 최대 수명. 최소 재호출 간격보다 길어야 업스트림 장애 때 만료된 캐시라도 돌려준다(R11). */
        val TTL: Duration = Duration.ofHours(1)
    }
}

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
