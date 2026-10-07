package org.around.ieuri.tourism.application.spot

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.catalog.required.SpotIdentity
import org.around.ieuri.tourism.application.catalog.required.SpotQueryPort
import org.around.ieuri.tourism.application.category.provided.exception.UnknownCategoryException
import org.around.ieuri.tourism.application.category.required.AppCategoryQueryPort
import org.around.ieuri.tourism.application.spot.provided.SpotDetailQueryUseCase
import org.around.ieuri.tourism.application.spot.provided.dto.SpotDetailView
import org.around.ieuri.tourism.application.spot.provided.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.spot.provided.exception.SpotNotFoundInLocaleQueryException
import org.around.ieuri.tourism.application.spot.required.SpotCachePort
import org.around.ieuri.tourism.application.spot.required.SpotDetailQuery
import org.around.ieuri.tourism.application.spot.required.SpotDetailQueryPort
import org.around.ieuri.tourism.application.spot.required.SpotEventPublisherPort
import org.around.ieuri.tourism.application.spot.required.TourInfoPort
import org.around.ieuri.tourism.domain.catalog.ReactionType
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * spot 상세 조회 구현(CQRS 읽기). Valkey → DB → TourAPI 순으로, 앞 단계에서 찾으면 뒤를 부르지 않는다.
 * - upstream에서 받아 온 값은 이벤트로 발행해 write-behind로 적재한다.
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class SpotDetailQueryService(
    private val tourInfoPort: TourInfoPort,
    private val spotQueryPort: SpotQueryPort,
    private val spotCachePort: SpotCachePort,
    private val detailQueryPort: SpotDetailQueryPort,
    private val eventPublisher: SpotEventPublisherPort,
    private val appCategoryQueryPort: AppCategoryQueryPort,
) : SpotDetailQueryUseCase {

    /**
     * 상세 본문. 캐시·DB가 모두 미스일 때만 upstream을 부른다. 하위 필드는 아래 메서드가 따로 조회한다.
     * - 부재와 실패를 섞지 않는다(SPEC-0008 R-1): 본문 부재는 적재하고 성공으로 응답하며, 호출 실패는 적재 없이 예외로 올린다.
     * - 적재된 부재 행은 조회 시점에 재확인하지 않는다 — 재확보는 주기 배치가 맡는다(SPEC-0037 FR-011).
     */
    override fun spotDetail(catalogId: Long, locale: DomainLocale): SpotDetailView {
        val identity = resolve(catalogId, locale)
        // 캐시·`spot_detail` 계열의 키는 카탈로그 id가 아니라 원본 `spot.id`다.
        val spotId = identity.spotId

        // 부재도 캐시된 값이다 — 히트로 취급한다.
        spotCachePort.getDetail(spotId)?.let { return it.toView(catalogId, identity) }

        // DB 히트: Valkey만 충전한다. 부재 행도 그대로 응답한다.
        detailQueryPort.findDetail(spotId)?.let { stored ->
            spotCachePort.putDetail(spotId, stored.detail)
            return stored.detail.toView(catalogId, identity)
        }

        // 양쪽 미스: 여기서 던져지는 예외는 실패이며, 이벤트 발행 전에 전파되므로 부재로 적재되지 않는다.
        val fetched = tourInfoPort.findDetail(identity.toQuery(locale))

        if (fetched == null) {
            // 부재 확정: `spot` 행의 값으로 응답하고 부재 사실을 적재한다.
            val fallback = bodyAbsentDetail(catalogId, locale)
            spotCachePort.putDetail(spotId, fallback)
            eventPublisher.publish(SpotDetailAbsent(spotId, fallback))
            return fallback.toView(catalogId, identity)
        }

        // 적재 upsert가 body_absent를 false로 되돌린다.
        spotCachePort.putDetail(spotId, fetched)
        eventPublisher.publish(SpotDetailFetched(spotId, fetched))
        return fetched.toView(catalogId, identity)
    }

    /**
     * 본문 부재 시 활성 언어의 카탈로그 값으로 상세를 구성한다(언어 폴백 아님).
     * - overview·homepage·tel은 null, address는 `spot`에 있으면 채운다.
     */
    private fun bodyAbsentDetail(catalogId: Long, language: DomainLocale): SpotDetail {
        // findSpotBasics는 카탈로그 id로 묻는다.
        val basics = spotQueryPort.findSpotBasics(catalogId, language) ?: throw spotAbsenceFor(catalogId)
        return SpotDetail.absent(basics.title, basics.category, basics.thumbnailImageUrl, basics.address, basics.copyright)
    }

    override fun spotGallery(catalogId: Long, locale: DomainLocale): List<SpotImage> {
        val identity = resolve(catalogId, locale)
        val spotId = identity.spotId
        spotCachePort.getGallery(spotId)?.let { return it }

        detailQueryPort.findGallery(spotId)?.let { stored ->
            spotCachePort.putGallery(spotId, stored)
            return stored
        }

        val fetched = tourInfoPort.findGallery(identity.toQuery(locale))
        spotCachePort.putGallery(spotId, fetched)
        eventPublisher.publish(SpotGalleryFetched(spotId, fetched))
        return fetched
    }

    override fun spotIntro(catalogId: Long, category: SpotCategory, locale: DomainLocale): SpotIntro? {
        requireKnownCategory(category)
        val identity = resolve(catalogId, locale)
        val spotId = identity.spotId
        // 히트 판정은 value가 아니라 래퍼로 한다 — 값은 정상적으로 null일 수 있다.
        val cached = spotCachePort.getIntro(spotId, category)
        if (cached != null) return cached.value

        val stored = detailQueryPort.findIntro(spotId, category)
        if (stored != null) {
            spotCachePort.putIntro(spotId, category, stored.value)
            return stored.value
        }

        val intro = tourInfoPort.findIntro(identity.toQuery(locale), category)
        spotCachePort.putIntro(spotId, category, intro)
        eventPublisher.publish(SpotIntroFetched(spotId, category, intro))
        return intro
    }

    override fun spotInfoItems(
        catalogId: Long,
        category: SpotCategory,
        locale: DomainLocale,
    ): List<SpotInfoItem> {
        requireKnownCategory(category)
        val identity = resolve(catalogId, locale)
        val spotId = identity.spotId
        spotCachePort.getInfoItems(spotId, category)
            ?.let { return it }

        detailQueryPort.findInfoItems(spotId, category)?.let { stored ->
            spotCachePort.putInfoItems(spotId, category, stored)
            return stored
        }

        val fetched = tourInfoPort.findInfoItems(identity.toQuery(locale), category)
        spotCachePort.putInfoItems(spotId, category, fetched)
        eventPublisher.publish(SpotInfoItemsFetched(spotId, category, fetched))
        return fetched
    }

    /** 좋아요 수. 캐시하지 않고 매번 집계한다. */
    override fun spotLikeCount(catalogId: Long, locale: DomainLocale): Long = spotQueryPort.countLikes(catalogId, locale)

    /** 최신 반응. 캐시하지 않는다. */
    override fun spotViewerReaction(catalogId: Long, memberId: Long, locale: DomainLocale): ReactionType? = spotQueryPort.findReaction(catalogId, memberId, locale)

    /**
     * 카탈로그 id를 활성 언어의 원본 행으로 해석한다(SPEC-0029).
     * - 캐시·DB를 보기 전에 호출해야 한다 — 캐시 키와 `spot_detail`에는 언어가 없다.
     * - 폴백하지 않는다: 활성 언어에 없으면 SPOT_NOT_FOUND_IN_LOCALE, 카탈로그 자체가 없으면 다른 코드로 실패한다(POL-043).
     */
    private fun resolve(catalogId: Long, locale: DomainLocale): SpotIdentity = spotQueryPort.findIdentity(catalogId, locale) ?: throw spotAbsenceFor(catalogId)

    /** 해소 실패 예외. 카탈로그가 있으면 "이 언어에 없음", 없으면 "그런 장소 없음". */
    private fun spotAbsenceFor(catalogId: Long): SpotQueryException = if (spotQueryPort.existsCatalog(catalogId)) {
        SpotNotFoundInLocaleQueryException()
    } else {
        SpotDetailNotFoundException()
    }

    /** upstream 조회 키. contentId가 언어별이라 [locale]은 이 contentId를 해석한 언어여야 한다. */
    private fun SpotIdentity.toQuery(locale: DomainLocale) = SpotDetailQuery(contentId = contentId, locale = locale)

    /** `category` 테이블에 정의된 카테고리인지 확인한다. 상세 하위 필드에만 건다. */
    private fun requireKnownCategory(category: SpotCategory) {
        if (!appCategoryQueryPort.exists(category.code)) {
            throw UnknownCategoryException("알 수 없는 카테고리입니다: ${category.code}")
        }
    }

    /** 본문에 카탈로그 id와 좌표를 얹는다. 좌표는 본문 출처와 무관하게 [identity]에서만 온다. */
    private fun SpotDetail.toView(catalogId: Long, identity: SpotIdentity): SpotDetailView = SpotDetailView(
        id = catalogId,
        title = title,
        category = category,
        thumbnailImageUrl = thumbnailImageUrl,
        overview = overview,
        address = address,
        homepage = homepage,
        tel = tel,
        copyright = copyright,
        mapX = identity.mapX,
        mapY = identity.mapY,
    )
}
