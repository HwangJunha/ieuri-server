package org.around.ieuri.tourism.infrastructure.adapter.out.cache

import org.around.ieuri.tourism.application.port.out.cache.CachedIntro
import org.around.ieuri.tourism.application.port.out.cache.SpotCachePort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * Valkey 기반 관광지 캐시(SPEC-0003 §4). auth의 refresh 토큰 저장소와 동일 인스턴스를 쓴다(신규 인프라 아님).
 *
 *  - **오퍼레이션별 키**: `ieuri:tourism:spot:{op}:{language}[:{category}]:{contentId}`.
 *    필드별 지연 조회와 짝을 맞춰 부분 히트가 가능하다. TTL 24h(AC-20).
 *  - **likeCount는 담지 않는다** — 캐시되는 SpotDetail 본문에 애초에 반응 수가 없다(AC-14).
 *  - **에러는 캐시하지 않는다**: 조회 실패는 예외로 전파되므로 put이 호출되지 않는다.
 *  - Valkey 장애·역직렬화 실패는 삼킨다(캐시는 최적화지 정합성 소스가 아님 → upstream 폴백).
 */
@Component
class ValkeySpotCache(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : SpotCachePort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getDetail(contentId: String, locale: DomainLocale): SpotDetail? =
        read(key("detail", locale, contentId)) {
            objectMapper.readValue(it, CachedSpotDetail::class.java).toDomain()
        }

    override fun putDetail(contentId: String, locale: DomainLocale, detail: SpotDetail) =
        write(key("detail", locale, contentId), CachedSpotDetail.from(detail))

    override fun getGallery(contentId: String, locale: DomainLocale): List<SpotImage>? =
        read(key("gallery", locale, contentId)) {
            objectMapper.readValue(it, object : TypeReference<List<CachedSpotImage>>() {}).map(CachedSpotImage::toDomain)
        }

    override fun putGallery(contentId: String, locale: DomainLocale, gallery: List<SpotImage>) =
        write(key("gallery", locale, contentId), gallery.map(CachedSpotImage::from))

    override fun getIntro(contentId: String, locale: DomainLocale, category: SpotCategory): CachedIntro? =
        read(key("intro", locale, contentId, category)) {
            // 저장된 값이 "null"이면 값 없음으로 히트 — 미스와 구분한다.
            CachedIntro(objectMapper.readValue(it, CachedSpotIntro::class.java)?.toDomain())
        }

    override fun putIntro(contentId: String, locale: DomainLocale, category: SpotCategory, intro: SpotIntro?) =
        write(key("intro", locale, contentId, category), intro?.let(CachedSpotIntro::from))

    override fun getInfoItems(contentId: String, locale: DomainLocale, category: SpotCategory): List<SpotInfoItem>? =
        read(key("info", locale, contentId, category)) {
            objectMapper.readValue(it, object : TypeReference<List<CachedSpotInfoItem>>() {})
                .map(CachedSpotInfoItem::toDomain)
        }

    override fun putInfoItems(
        contentId: String,
        locale: DomainLocale,
        category: SpotCategory,
        items: List<SpotInfoItem>,
    ) = write(key("info", locale, contentId, category), items.map { CachedSpotInfoItem(it.name, it.text) })

    /** 캐시 조회 공통. 저장소·역직렬화 실패는 삼켜서 upstream 폴백(캐시는 정합성 소스가 아님). */
    private fun <T> read(key: String, parse: (String) -> T): T? =
        try {
            redis.opsForValue().get(key)?.let(parse)
        } catch (e: Exception) {
            log.warn("관광지 캐시 조회 실패, upstream 폴백: {}", e.javaClass.simpleName)
            null
        }

    /** 캐시 저장 공통. 실패는 삼킨다(조회 실패로 이어지지 않게). */
    private fun write(key: String, value: Any?) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), TTL)
        } catch (e: Exception) {
            log.warn("관광지 캐시 저장 실패: {}", e.javaClass.simpleName)
        }
    }

    private fun key(operation: String, locale: DomainLocale, contentId: String, category: SpotCategory? = null) =
        buildString {
            append(KEY_PREFIX).append(operation).append(':').append(locale.name).append(':')
            category?.let { append(it.name).append(':') }
            append(contentId)
        }

    private companion object {
        const val KEY_PREFIX = "ieuri:tourism:spot:"
        val TTL: Duration = Duration.ofHours(24)
    }
}
