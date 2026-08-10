package org.around.ieuri.tourism.infrastructure.adapter.out.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.application.port.out.cache.CachedCitydata
import org.around.ieuri.tourism.application.port.out.cache.SeoulCitydataCachePort
import org.around.ieuri.tourism.application.port.out.client.SeoulCitydataSnapshot
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
 * 서울 실시간 도시데이터 Valkey 캐시(SPEC-0014 R13·R14). auth의 refresh 토큰 저장소와 같은
 * 인스턴스를 쓴다(신규 인프라 아님).
 *
 *  - **키**: `ieuri:tourism:citydata:{areaCd}`. 키에 업스트림 타임스탬프를 넣지 않는다 —
 *    넣으면 이전 시각의 키가 TTL까지 남아 메모리를 갉아먹고 "현재 값"을 찾으려면 키를
 *    스캔해야 한다. 저장 시각은 **값 안에** 둔다.
 *  - **TTL은 재호출 판정이 아니라 상한이다.** 신선도 판정은 값 안의 저장 시각으로
 *    서비스가 하고(R13), TTL은 버려진 구역이 영원히 남지 않게 하는 안전장치다.
 *  - Valkey 장애·역직렬화 실패는 삼킨다 — 캐시는 정합성 소스가 아니다. auth 쪽은
 *    fail-closed(POL-008)로 **따로** 동작하므로 여기서 삼켜도 영향이 없다.
 */
@Component
class ValkeySeoulCitydataCache(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : SeoulCitydataCachePort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun get(areaCode: String): CachedCitydata? =
        try {
            redis.opsForValue().get(key(areaCode))?.let {
                objectMapper.readValue(it, CachedCitydataPayload::class.java).toDomain()
            }
        } catch (e: Exception) {
            log.warn("서울 실시간 도시데이터 캐시 조회 실패, upstream 폴백: {}", e.javaClass.simpleName)
            null
        }

    override fun put(areaCode: String, snapshot: SeoulCitydataSnapshot, storedAt: Instant) {
        try {
            val payload = CachedCitydataPayload.from(snapshot, storedAt)
            redis.opsForValue().set(key(areaCode), objectMapper.writeValueAsString(payload), TTL)
        } catch (e: Exception) {
            log.warn("서울 실시간 도시데이터 캐시 저장 실패: {}", e.javaClass.simpleName)
        }
    }

    private fun key(areaCode: String) = "$KEY_PREFIX$areaCode"

    private companion object {
        const val KEY_PREFIX = "ieuri:tourism:citydata:"

        /**
         * 값의 최대 수명. 최소 재호출 간격(기본 5분)보다 길게 잡는다 — 업스트림이 장기
         * 장애일 때 만료된 캐시라도 돌려주려면(R11) 값이 남아 있어야 한다.
         *
         * **1시간이다.** 업스트림 데이터가 이미 약 30분 지연되어 도착하므로(실측 28~32분),
         * 여기에 1시간이 더해지면 최악의 경우 **1시간 반 전 상황**을 보여주게 된다. 그보다
         * 오래된 혼잡도는 "지금 붐비는가"라는 물음에 답하지 못해 보여줄 가치가 없다.
         */
        val TTL: Duration = Duration.ofHours(1)
    }
}

/**
 * 캐시 직렬화 DTO. 도메인 타입을 그대로 쓰지 않는 것은 기존 [CachedSpotDetail]과 같은
 * 방침이다 — 도메인이 바뀌어도 캐시에 남은 옛 항목이 역직렬화를 깨뜨리지 않게 한다.
 *
 * 모든 필드에 기본값이 있어 필드가 추가돼도 옛 항목이 읽힌다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedCitydataPayload(
    val areaCode: String = "",
    val storedAtEpochSecond: Long = 0,
    val congestion: CachedSnapshot? = null,
    val forecast: List<CachedForecast>? = null,
    val commercial: CachedCommercial? = null,
    val events: List<CachedEvent> = emptyList(),
) {
    fun toDomain() = CachedCitydata(
        snapshot = SeoulCitydataSnapshot(
            areaCode = areaCode,
            congestion = congestion?.toDomain(),
            forecast = forecast?.map(CachedForecast::toDomain),
            commercial = commercial?.toDomain(),
            events = events.map(CachedEvent::toDomain),
        ),
        storedAt = Instant.ofEpochSecond(storedAtEpochSecond),
    )

    companion object {
        fun from(snapshot: SeoulCitydataSnapshot, storedAt: Instant) = CachedCitydataPayload(
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
        fun from(v: CongestionSnapshot) =
            CachedSnapshot(v.level, v.populationMin, v.populationMax, v.observedAt.toString())
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
        fun from(v: CongestionForecast) =
            CachedForecast(v.forecastTime.toString(), v.level, v.populationMin, v.populationMax)
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
