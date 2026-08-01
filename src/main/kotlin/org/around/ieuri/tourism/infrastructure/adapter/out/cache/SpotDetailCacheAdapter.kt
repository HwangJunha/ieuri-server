package org.around.ieuri.tourism.infrastructure.adapter.out.cache

import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.NearbySpotResult
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.infrastructure.adapter.out.tourapi.TourApiClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * TourInfoPort 캐시 데코레이터(SPEC-0003 §4). 상세 조회의 **TourAPI 파생 필드**(overview·gallery·contacts·copyright)를
 * Valkey에 캐시해 반복 진입 비용을 낮춘다. 캐시 미스에서만 실제 TourAPI 2콜([TourApiClient])이 발생한다.
 *
 * 경계 원칙:
 *  - 캐시 대상은 파생 필드뿐. **likeCount는 절대 캐시하지 않는다**(application이 매 조회 실시간 집계로 얹음, AC-14).
 *    findDetail이 반환하는 SpotDetail은 likeCount=0(집계 전)이라 캐시 값에 반응 수가 섞이지 않는다.
 *  - 캐시 키 = (contentId, language). contentId가 언어별로 갈리므로 언어를 키에 넣어 KO/EN 상세가 안 섞이게 한다(AC-19).
 *  - TTL 24h. 준정적 콘텐츠라 최대 24h 지연 감수(AC-20).
 *  - **에러는 캐시하지 않는다**: NotFound/RateLimited/Unavailable는 예외로 전파되며 저장되지 않아 다음 조회가 재시도된다.
 *
 * findNearby는 캐시하지 않고 그대로 위임한다(SPEC-0002 범위).
 *
 * 데코레이터는 @Primary라 application(TourInfoPort 주입처)이 이 빈을 받는다. 도메인·GraphQL은 캐시 존재를 모른다.
 * Valkey 장애 시엔 캐시를 건너뛰고 upstream으로 폴백한다(캐시는 최적화지 정합성 소스가 아님).
 */
@Component
@Primary
class SpotDetailCacheAdapter(
    private val delegate: TourApiClient,
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : TourInfoPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun findNearby(query: NearbySpotQuery): NearbySpotResult = delegate.findNearby(query)

    override fun findDetail(query: SpotDetailQuery): SpotDetail {
        val key = cacheKey(query)
        readFromCache(key)?.let { return it }

        val detail = delegate.findDetail(query)
        writeToCache(key, detail)
        return detail
    }

    private fun cacheKey(query: SpotDetailQuery): String = "$KEY_PREFIX${query.locale.name}:${query.contentId}"

    /** 캐시 조회. 역직렬화·연결 실패는 삼켜서 upstream 폴백(캐시는 정합성 소스가 아님). */
    private fun readFromCache(key: String): SpotDetail? =
        try {
            redis.opsForValue().get(key)
                ?.let { objectMapper.readValue(it, CachedSpotDetail::class.java) }
                ?.toDomain()
        } catch (e: Exception) {
            log.warn("상세 캐시 조회 실패, upstream 폴백: {}", e.javaClass.simpleName)
            null
        }

    /** 파생 필드만 캐시(likeCount는 담지 않음 — 실시간 집계로 대체됨). 쓰기 실패는 삼킨다. */
    private fun writeToCache(key: String, detail: SpotDetail) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(CachedSpotDetail.from(detail)), TTL)
        } catch (e: Exception) {
            log.warn("상세 캐시 저장 실패: {}", e.javaClass.simpleName)
        }
    }

    private companion object {
        const val KEY_PREFIX = "ieuri:tourism:spot-detail:"
        val TTL: Duration = Duration.ofHours(24)
    }
}
