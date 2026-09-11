package org.around.ieuri.tourism.infrastructure.adapter.out.cache

import org.around.ieuri.tourism.application.port.out.cache.FeedBatchCachePort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.infrastructure.config.RecommendationProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * 추천 피드 묶음 캐시(SPEC-0035 R17). [ValkeySpotCache]와 같은 인스턴스, 키
 * `ieuri:tourism:feed:{조건 해시}:{묶음 번호}`, TTL은 설정.
 *
 * 실패를 여기서 삼키지 않는다 — 호출하는 서비스가 조회·저장 실패를 모두 재계산으로 흡수한다
 * (AC-17.3). 두 곳에서 삼키면 어느 쪽이 책임인지 흐려진다.
 */
@Component
class ValkeyFeedBatchCache(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val properties: RecommendationProperties,
) : FeedBatchCachePort {

    override fun get(key: String, batchNo: Int): List<SpotSearchResult>? =
        redis.opsForValue().get(cacheKey(key, batchNo))?.let { json ->
            objectMapper.readValue(json, object : TypeReference<List<CachedFeedCard>>() {})
                .map(CachedFeedCard::toSearchResult)
        }

    override fun put(key: String, batchNo: Int, cards: List<SpotSearchResult>) {
        redis.opsForValue().set(
            cacheKey(key, batchNo),
            objectMapper.writeValueAsString(cards.map(CachedFeedCard::from)),
            properties.cache.ttl,
        )
    }

    private fun cacheKey(key: String, batchNo: Int) = "$KEY_PREFIX$key:$batchNo"

    private companion object {
        const val KEY_PREFIX = "ieuri:tourism:feed:"
    }
}
