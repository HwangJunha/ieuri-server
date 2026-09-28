package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.event.SpotDetailAbsent
import org.around.ieuri.tourism.application.event.SpotDetailFetched
import org.around.ieuri.tourism.application.event.SpotGalleryFetched
import org.around.ieuri.tourism.application.event.SpotInfoItemsFetched
import org.around.ieuri.tourism.application.event.SpotIntroFetched
import org.around.ieuri.tourism.application.exception.EmptyQueryException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.QueryTooLongException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.exception.SpotNotFoundInLocaleQueryException
import org.around.ieuri.tourism.application.exception.SpotQueryException
import org.around.ieuri.tourism.application.exception.UnknownCategoryException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.port.out.cache.SpotCachePort
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.application.port.out.persistence.StoredDetail
import org.around.ieuri.tourism.application.port.out.query.AppCategoryQueryPort
import org.around.ieuri.tourism.application.port.out.query.LikedSpotSearchPort
import org.around.ieuri.tourism.application.port.out.query.LikedSpotsSearch
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotIdentity
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.query.AppCategoryCode
import org.around.ieuri.tourism.application.query.AppCategoryView
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.application.query.LikedSpotsQuery
import org.around.ieuri.tourism.application.query.SpotDetailView
import org.around.ieuri.tourism.application.query.SpotImageView
import org.around.ieuri.tourism.application.query.SpotInfoItemView
import org.around.ieuri.tourism.application.query.SpotIntroView
import org.around.ieuri.tourism.application.query.SpotPageView
import org.around.ieuri.tourism.application.query.SpotSummaryView
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotIntro
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 관광지 조회 유스케이스 구현(CQRS 읽기).
 * - 목록은 적재된 spot 테이블만 본다. 상세는 Valkey → DB → TourAPI 순으로, 앞 단계에서 찾으면 뒤를 부르지 않는다.
 */
@Service
@Transactional(readOnly = true)
class SpotQueryService(
    private val tourInfoPort: TourInfoPort,
    private val spotQueryPort: SpotQueryPort,
    private val spotSpatialQueryPort: SpotSpatialQueryPort,
    private val likedSpotSearchPort: LikedSpotSearchPort,
    private val spotCachePort: SpotCachePort,
    private val spotDetailStorePort: SpotDetailStorePort,
    private val eventPublisher: TourismEventPublisherPort,
    private val appCategoryQueryPort: AppCategoryQueryPort,
    private val policy: SpotQueryPolicy,
) : SpotQueryUseCase {

    /** 주변 관광지 한 페이지. TourAPI를 호출하지 않으며, 거리 오름차순 + id 타이브레이크로 정렬된다. */
    override fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): SpotPageView {
        val center = validatedCenter(query.latitude, query.longitude)
        validateRadius(query.radiusMeters)

        val page = query.page.coerceAtLeast(1)
        val size = query.size.coerceIn(1, policy.maxPageSize)

        // 회원이 활성 언어에서 이미 반응한 관광지는 쿼리 단계에서 제외한다(SPEC-0029 R-13). 비로그인은 제외 없음.
        val excluded = memberId?.let { spotQueryPort.findReactedSpotIds(it, locale) }.orEmpty()

        val search = NearbySpotSearch(
            language = locale,
            center = center,
            radiusMeters = query.radiusMeters,
            categories = query.categories,
            excludedSpotIds = excluded,
            limit = size,
            offset = (page - 1) * size,
        )

        val results = spotSpatialQueryPort.searchNearby(search)
        val totalCount = spotSpatialQueryPort.countNearby(search)

        return SpotPageView(
            items = results.map { it.toView() },
            page = page,
            // totalCount와 같은 조건으로 판정해야 둘이 모순되지 않는다.
            hasNext = page.toLong() * size < totalCount.toLong(),
            totalCount = totalCount,
        )
    }

    override fun availableCategories(appKey: AppKey, locale: DomainLocale): List<AppCategoryView> = appCategoryQueryPort.findCategories(appKey, locale)

    override fun availableCategoryCodes(appKey: AppKey, languageCode: String): List<AppCategoryCode> = availableCategories(appKey, DomainLocale.fromLanguageTag(languageCode))
        .map {
            AppCategoryCode(
                category = it.category.code,
                label = it.label,
                iconUrl = it.iconUrl,
            )
        }

    /** 요청한 모든 코드에 값이 있다 — 표시 정보가 없는 코드는 폴백으로 채운다. */
    override fun categoryDisplays(codes: Set<String>, locale: DomainLocale): Map<String, CategoryDisplay> {
        val found = appCategoryQueryPort.findDisplays(codes, locale)
        return codes.associateWith { found[it] ?: CategoryDisplay.fallback(SpotCategory(it)) }
    }

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
        spotCachePort.getDetail(spotId)?.let { return it.detail.toView(catalogId, identity) }

        // DB 히트: Valkey만 충전한다. 부재 행도 그대로 응답한다.
        spotDetailStorePort.findDetail(spotId)?.let { stored ->
            if (stored.bodyAbsent) {
                spotCachePort.putDetailAbsence(spotId, stored.detail)
            } else {
                spotCachePort.putDetail(spotId, stored.detail)
            }
            return stored.detail.toView(catalogId, identity)
        }

        // 양쪽 미스: 여기서 던져지는 예외는 실패이며, 이벤트 발행 전에 전파되므로 부재로 적재되지 않는다.
        val fetched = tourInfoPort.findDetail(identity.toQuery(locale))

        if (fetched == null) {
            // 부재 확정: `spot` 행의 값으로 응답하고 부재 사실을 적재한다.
            val fallback = bodyAbsentDetail(catalogId, locale)
            spotCachePort.putDetailAbsence(spotId, fallback)
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
        return SpotDetail(
            title = basics.title,
            category = basics.category,
            thumbnailImageUrl = basics.thumbnailImageUrl,
            overview = null,
            address = basics.address,
            homepage = null,
            tel = null,
            copyright = basics.copyright,
        )
    }

    override fun spotGallery(catalogId: Long, locale: DomainLocale): List<SpotImageView> {
        val identity = resolve(catalogId, locale)
        val spotId = identity.spotId
        spotCachePort.getGallery(spotId)?.let { return it.map { image -> image.toView() } }

        spotDetailStorePort.findGallery(spotId)?.let { stored ->
            spotCachePort.putGallery(spotId, stored)
            return stored.map { it.toView() }
        }

        val fetched = tourInfoPort.findGallery(identity.toQuery(locale))
        spotCachePort.putGallery(spotId, fetched)
        eventPublisher.publish(SpotGalleryFetched(spotId, fetched))
        return fetched.map { it.toView() }
    }

    override fun spotIntro(catalogId: Long, category: SpotCategory, locale: DomainLocale): SpotIntroView? {
        requireKnownCategory(category)
        val identity = resolve(catalogId, locale)
        val spotId = identity.spotId
        // 히트 판정은 value가 아니라 래퍼로 한다 — 값은 정상적으로 null일 수 있다.
        val cached = spotCachePort.getIntro(spotId, category)
        if (cached != null) return cached.value?.toView()

        val stored = spotDetailStorePort.findIntro(spotId, category)
        if (stored != null) {
            spotCachePort.putIntro(spotId, category, stored.value)
            return stored.value?.toView()
        }

        val intro = tourInfoPort.findIntro(identity.toQuery(locale), category)
        spotCachePort.putIntro(spotId, category, intro)
        eventPublisher.publish(SpotIntroFetched(spotId, category, intro))
        return intro?.toView()
    }

    override fun spotInfoItems(
        catalogId: Long,
        category: SpotCategory,
        locale: DomainLocale,
    ): List<SpotInfoItemView> {
        requireKnownCategory(category)
        val identity = resolve(catalogId, locale)
        val spotId = identity.spotId
        spotCachePort.getInfoItems(spotId, category)
            ?.let { return it.map { item -> SpotInfoItemView(item.name, item.text) } }

        spotDetailStorePort.findInfoItems(spotId, category)?.let { stored ->
            spotCachePort.putInfoItems(spotId, category, stored)
            return stored.map { SpotInfoItemView(it.name, it.text) }
        }

        val fetched = tourInfoPort.findInfoItems(identity.toQuery(locale), category)
        spotCachePort.putInfoItems(spotId, category, fetched)
        eventPublisher.publish(SpotInfoItemsFetched(spotId, category, fetched))
        return fetched.map { SpotInfoItemView(it.name, it.text) }
    }

    /** 좋아요 수. 캐시하지 않고 매번 집계한다. */
    override fun spotLikeCount(catalogId: Long, locale: DomainLocale): Long = spotQueryPort.countLikes(catalogId, locale)

    /** 최신 반응 코드. 캐시하지 않는다. */
    override fun spotViewerReaction(catalogId: Long, memberId: Long, locale: DomainLocale): String? = spotQueryPort.findReaction(catalogId, memberId, locale)?.name

    /**
     * 카탈로그 여러 행을 읽는다. 캐시도 upstream도 타지 않는다.
     * - [languageCode]에서 해소되지 않는 id는 결과에 키가 없다(SPEC-0029 R-24).
     */
    override fun spotSummaries(catalogIds: List<Long>, languageCode: String): Map<Long, SpotSummaryView> = spotQueryPort.findSpotBasicsIn(catalogIds, DomainLocale.fromLanguageTag(languageCode))
        .mapValues { (catalogId, basics) ->
            SpotSummaryView(
                id = catalogId,
                title = basics.title,
                thumbnailImageUrl = basics.thumbnailImageUrl,
                categoryCode = basics.category.code,
                longitude = basics.longitude,
                latitude = basics.latitude,
            )
        }

    /** 카탈로그 한 행을 읽는다. [languageCode]에서 해소되지 않으면 null이다 — 다른 언어로 대신하지 않는다. */
    override fun spotSummary(catalogId: Long, languageCode: String): SpotSummaryView? = spotQueryPort.findSpotBasics(catalogId, DomainLocale.fromLanguageTag(languageCode))?.let {
        SpotSummaryView(
            id = catalogId,
            title = it.title,
            thumbnailImageUrl = it.thumbnailImageUrl,
            categoryCode = it.category.code,
            longitude = it.longitude,
            latitude = it.latitude,
        )
    }

    /**
     * 좋아요 목록(SPEC-0008). 본인의 LIKE만, 요청 언어만 조회한다. 페이지 크기는 상한으로 절삭한다.
     * - 상세가 부재한 spot도 목록에 나온다.
     */
    override fun likedSpots(
        query: LikedSpotsQuery,
        locale: DomainLocale,
        memberId: Long,
    ): CursorPage<LikedSpotView> = spotQueryPort.findLikedSpots(searchOf(query, locale, memberId))

    /**
     * 좋아요 목록 내 검색(SPEC-0033). 검색 노드를 보며, 정렬·커서 규약은 [likedSpots]와 같다.
     * - 검색어 검증은 포트 호출보다 먼저 한다.
     */
    override fun searchLikedSpots(
        query: LikedSpotsQuery,
        text: String,
        locale: DomainLocale,
        memberId: Long,
    ): CursorPage<LikedSpotView> = likedSpotSearchPort.searchLikedSpots(searchOf(query, locale, memberId), validatedSearchText(text))

    /** 좋아요 목록과 그 안의 검색이 공유하는 조회 조건. */
    private fun searchOf(query: LikedSpotsQuery, locale: DomainLocale, memberId: Long) = LikedSpotsSearch(
        memberId = memberId,
        language = locale,
        categories = query.categories,
        first = query.first.coerceIn(1, policy.maxLikedPageSize),
        after = query.after,
    )

    /**
     * 검색어를 트림해 돌려준다. 트림 후 비었으면 EMPTY_QUERY, 트림 후 길이가 상한을 넘으면 QUERY_TOO_LONG.
     * - 예외 메시지에 원문을 담지 않는다(POL-013).
     */
    private fun validatedSearchText(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> throw EmptyQueryException()
            trimmed.length > policy.maxSearchQueryLength -> throw QueryTooLongException(policy.maxSearchQueryLength)
            else -> trimmed
        }
    }

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

    private fun validatedCenter(latitude: Double, longitude: Double): Coordinates = try {
        Coordinates(latitude, longitude)
    } catch (e: IllegalArgumentException) {
        throw InvalidCoordinatesException(e.message ?: "유효하지 않은 좌표입니다.")
    }

    private fun validateRadius(radiusMeters: Int) {
        if (radiusMeters <= 0) throw InvalidCoordinatesException("반경은 양수여야 합니다.")
        if (radiusMeters > policy.maxRadiusMeters) {
            throw RadiusTooLargeException("반경은 최대 ${policy.maxRadiusMeters}m까지 허용됩니다.")
        }
    }

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
        attributionText = attributionText,
        mapX = identity.mapX,
        mapY = identity.mapY,
    )

    private fun SpotIntro.toView(): SpotIntroView = SpotIntroView(
        businessHours = businessHours,
        closedDays = closedDays,
        parking = parking,
        inquiryPhone = inquiryPhone,
        fee = fee,
        petAllowed = petAllowed,
        creditCardAccepted = creditCardAccepted,
    )

    private fun SpotImage.toView(): SpotImageView = SpotImageView(
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        caption = caption,
        copyright = copyright,
        attributionText = attributionText,
    )

    /** 읽기 DTO → 응답 뷰. attributionText는 copyright에서 도출한다. */
    private fun SpotSearchResult.toView(): SpotView = SpotView(
        id = spotId,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        distanceMeters = distanceMeters,
        category = category,
        copyright = copyright,
        attributionText = copyright.attributionText,
        mapX = mapX,
        mapY = mapY,
    )
}
