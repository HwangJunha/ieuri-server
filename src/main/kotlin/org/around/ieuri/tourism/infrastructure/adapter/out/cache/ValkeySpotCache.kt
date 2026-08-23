package org.around.ieuri.tourism.infrastructure.adapter.out.cache

import org.around.ieuri.tourism.application.port.out.cache.CachedDetail
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
 *  - **오퍼레이션별 키**: `ieuri:tourism:spot:{op}:{language}[:{category}]:{spotId}`.
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

    /**
     * 본문 캐시. **부재도 캐시된 값이다**(SPEC-0008 R-3) — 래퍼가 부재 여부를 함께 돌려주므로
     * 호출자가 "미스"와 "부재를 확인함"을 구분한다. 부재를 미스로 오판하면 본문 없는 spot이
     * TTL 내내 매 조회마다 DB·upstream을 타게 된다.
     */
    override fun getDetail(spotId: Long): CachedDetail? =
        read(key("detail", spotId)) {
            val cached = objectMapper.readValue(it, CachedSpotDetail::class.java)
            CachedDetail(detail = cached.toDomain(), bodyAbsent = cached.bodyAbsent)
        }

    override fun putDetail(spotId: Long, detail: SpotDetail) =
        write(key("detail", spotId), CachedSpotDetail.from(detail))

    override fun putDetailAbsence(spotId: Long, fallback: SpotDetail) =
        write(key("detail", spotId), CachedSpotDetail.from(fallback, bodyAbsent = true))

    override fun getGallery(spotId: Long): List<SpotImage>? =
        read(key("gallery", spotId)) {
            objectMapper.readValue(it, object : TypeReference<List<CachedSpotImage>>() {}).map(CachedSpotImage::toDomain)
        }

    override fun putGallery(spotId: Long, gallery: List<SpotImage>) =
        write(key("gallery", spotId), gallery.map(CachedSpotImage::from))

    override fun getIntro(spotId: Long, category: SpotCategory): CachedIntro? =
        read(key("intro", spotId, category)) {
            // 저장된 값이 "null"이면 값 없음으로 히트 — 미스와 구분한다.
            CachedIntro(objectMapper.readValue(it, CachedSpotIntro::class.java)?.toDomain())
        }

    override fun putIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?) =
        write(key("intro", spotId, category), intro?.let(CachedSpotIntro::from))

    override fun getInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>? =
        read(key("info", spotId, category)) {
            objectMapper.readValue(it, object : TypeReference<List<CachedSpotInfoItem>>() {})
                .map(CachedSpotInfoItem::toDomain)
        }

    override fun putInfoItems(
        spotId: Long,
        category: SpotCategory,
        items: List<SpotInfoItem>,
    ) = write(key("info", spotId, category), items.map { CachedSpotInfoItem(it.name, it.text) })

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

    /**
     * 캐시 키. **언어 세그먼트가 없다** — [spotId]가 이미 언어별로 갈리므로(같은 장소의 다른 언어는
     * 다른 `spot.id`) 언어를 다시 넣으면 중복이다.
     *
     * 프리픽스에 `v2`가 붙는다. 식별자가 TourAPI spotId에서 `spot.id`로 바뀌면서 **같은 키 모양에
     * 다른 의미가 담기게 되므로**, 옛 항목이 새 키로 잘못 읽히지 않도록 네임스페이스를 분리한다.
     * 남은 v1 항목은 TTL(24h)로 자연 소멸한다.
     */
    private fun key(operation: String, spotId: Long, category: SpotCategory? = null) =
        buildString {
            append(KEY_PREFIX).append(operation).append(':')
            category?.let { append(it.code).append(':') }
            append(spotId)
        }

    private companion object {
        const val KEY_PREFIX = "ieuri:tourism:spot:v2:"
        val TTL: Duration = Duration.ofHours(24)
    }
}
