package org.around.ieuri.tourism.adapter.cache

import org.around.ieuri.tourism.application.spot.required.KnownIntro
import org.around.ieuri.tourism.application.spot.required.SpotCachePort
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * Valkey 기반 관광지 캐시(SPEC-0003 §4). 오퍼레이션별 키라 부분 히트가 가능하다. TTL 24h(AC-20).
 * - 좋아요 수는 담지 않는다(AC-14). 조회 실패는 캐시하지 않는다.
 * - Valkey 장애·역직렬화 실패는 삼킨다(upstream 폴백).
 */
@Component
class ValkeySpotCache(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : SpotCachePort {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 본문 캐시. 부재로 확인돼 `spot` 행에서 구성한 값도 캐시된 값이다(SPEC-0008 R-3). */
    override fun getDetail(spotId: Long): SpotDetail? = read(key("detail", spotId)) {
        objectMapper.readValue(it, CachedSpotDetail::class.java).toDomain()
    }

    override fun putDetail(spotId: Long, detail: SpotDetail) = write(key("detail", spotId), CachedSpotDetail.from(detail))

    override fun getGallery(spotId: Long): List<SpotImage>? = read(key("gallery", spotId)) {
        objectMapper.readValue(it, object : TypeReference<List<CachedSpotImage>>() {}).map(CachedSpotImage::toDomain)
    }

    override fun putGallery(spotId: Long, gallery: List<SpotImage>) = write(key("gallery", spotId), gallery.map(CachedSpotImage::from))

    override fun getIntro(spotId: Long, category: SpotCategory): KnownIntro? = read(key("intro", spotId, category)) {
        // 저장된 값이 "null"이면 값 없음으로 히트 — 미스와 구분한다.
        KnownIntro(objectMapper.readValue(it, CachedSpotIntro::class.java)?.toDomain())
    }

    override fun putIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?) = write(key("intro", spotId, category), intro?.let(CachedSpotIntro::from))

    override fun getInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>? = read(key("info", spotId, category)) {
        objectMapper.readValue(it, object : TypeReference<List<CachedSpotInfoItem>>() {})
            .map(CachedSpotInfoItem::toDomain)
    }

    override fun putInfoItems(
        spotId: Long,
        category: SpotCategory,
        items: List<SpotInfoItem>,
    ) = write(key("info", spotId, category), items.map { CachedSpotInfoItem(it.name, it.text) })

    /** 캐시 조회 공통. 저장소·역직렬화 실패는 삼킨다. */
    private fun <T> read(key: String, parse: (String) -> T): T? = try {
        redis.opsForValue().get(key)?.let(parse)
    } catch (e: Exception) {
        log.warn("관광지 캐시 조회 실패, upstream 폴백: {}", e.javaClass.simpleName)
        null
    }

    /** 캐시 저장 공통. 실패는 삼킨다. */
    private fun write(key: String, value: Any?) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), TTL)
        } catch (e: Exception) {
            log.warn("관광지 캐시 저장 실패: {}", e.javaClass.simpleName)
        }
    }

    /** 캐시 키. [spotId]가 언어별이라 언어 세그먼트가 없다. 프리픽스 `v2`는 옛 식별자 체계의 항목과 분리한다. */
    private fun key(operation: String, spotId: Long, category: SpotCategory? = null) = buildString {
        append(KEY_PREFIX).append(operation).append(':')
        category?.let { append(it.code).append(':') }
        append(spotId)
    }

    private companion object {
        const val KEY_PREFIX = "ieuri:tourism:spot:v2:"
        val TTL: Duration = Duration.ofHours(24)
    }
}
