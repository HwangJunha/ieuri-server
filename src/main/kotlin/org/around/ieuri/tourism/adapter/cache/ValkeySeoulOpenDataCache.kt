package org.around.ieuri.tourism.adapter.cache

import org.around.ieuri.tourism.application.congestion.required.SeoulOpenDataCachePort
import org.around.ieuri.tourism.application.congestion.required.dto.CachedSeoulOpenData
import org.around.ieuri.tourism.application.congestion.required.dto.SeoulOpenDataSnapshot
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
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
