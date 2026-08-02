package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.exception.CategoryNotAvailableForLocaleException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.port.out.cache.SpotCachePort
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.application.event.SpotDetailFetched
import org.around.ieuri.tourism.application.event.SpotGalleryFetched
import org.around.ieuri.tourism.application.event.SpotInfoItemsFetched
import org.around.ieuri.tourism.application.event.SpotIntroFetched
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.query.SpotDetailView
import org.around.ieuri.tourism.application.query.SpotImageView
import org.around.ieuri.tourism.application.query.SpotInfoItemView
import org.around.ieuri.tourism.application.query.SpotIntroView
import org.around.ieuri.tourism.application.query.SpotPageView
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotIntro
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.stereotype.Service

/**
 * 관광지 조회 유스케이스 구현(CQRS 읽기).
 *
 * **목록(spots)과 상세(spotDetail)의 데이터 출처가 다르다**(SPEC-0004):
 *  - 목록 : 적재된 spot 테이블만 본다. TourAPI를 호출하지 않는다(R-8). 반경 필터·거리 계산·정렬은
 *           DB가 인덱스로 수행한다(R-9). 좌표/반경/카테고리 검증만 여기서 한다.
 *  - 상세 : **Valkey → DB → TourAPI 3단 폴백**(R-13). 상세는 단건 조회만 지원되고 호출 쿼터로
 *           전량 동기화가 불가능해, 조회된 것만 write-behind로 누적한다. 앞 단계에서 값을 찾으면
 *           뒤 단계를 호출하지 않으며, 필드별 지연 조회 구조는 그대로다(SPEC-0003 R7).
 *
 * 페이지네이션은 오프셋(page/size)을 유지한다. 안정적 백킹 스토어가 생겨 커서가 기술적으로
 * 가능해졌지만(POL-017), 전환은 별도 스펙으로 다룬다(SPEC-0004 범위 제외).
 */
@Service
class SpotQueryService(
    private val tourInfoPort: TourInfoPort,
    private val spotQueryPort: SpotQueryPort,
    private val spotCachePort: SpotCachePort,
    private val spotDetailStorePort: SpotDetailStorePort,
    private val eventPublisher: TourismEventPublisherPort,
    private val policy: SpotQueryPolicy,
) : SpotQueryUseCase {

    /**
     * 주변 관광지 한 페이지(SPEC-0004 R-8). **TourAPI를 호출하지 않는다** — 배치가 적재한 spot
     * 테이블만 본다(AC-20·AC-21). 반경 필터·거리 계산·정렬을 DB가 인덱스로 수행한다(AC-24).
     *
     * SPEC-0002와 달라진 점은 거리·정렬의 주체다. 당시에는 upstream이 반경 안의 결과만 줘서
     * application이 Haversine으로 채우고 정렬했지만, 전량 적재 후에는 "반경 안"을 DB가 풀어야 한다.
     * 계약(거리 오름차순 + id 타이브레이크, POL-019)은 그대로이며 ORDER BY로 옮겨갔다.
     */
    override fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): SpotPageView {
        val center = validatedCenter(query.latitude, query.longitude)
        validateRadius(query.radiusMeters)
        query.category?.let { validateCategory(it, locale) }

        val page = query.page.coerceAtLeast(1)
        val size = query.size.coerceIn(1, policy.maxPageSize)

        // 로그인 회원이 이미 반응(좋아요/패스)한 관광지 제외 — 중복 노출 방지. 비로그인은 제외 없음.
        // **DB 쿼리 단계에서 제외한다** — 조회 후 걸러내면 페이지가 요청한 size보다 작아진다.
        val excluded = memberId?.let { spotQueryPort.findReactedContentIds(it, locale) }.orEmpty()

        val search = NearbySpotSearch(
            language = locale,
            center = center,
            radiusMeters = query.radiusMeters,
            category = query.category,
            excludedContentIds = excluded,
            limit = size,
            offset = (page - 1) * size,
        )

        val results = spotQueryPort.searchNearby(search)
        val totalCount = spotQueryPort.countNearby(search)

        return SpotPageView(
            items = results.map { it.toView() },
            page = page,
            // 같은 조건의 전체 건수 기준이라 totalCount와 일관된다 — 둘이 갈리면
            // hasNext=false인데 totalCount는 더 있다고 말하는 상태가 생긴다(런타임 계약 위험 3번).
            hasNext = page.toLong() * size < totalCount.toLong(),
            totalCount = totalCount,
        )
    }

    override fun availableCategories(locale: DomainLocale): List<SpotCategory> =
        SpotCategory.availableIn(locale)

    /**
     * 상세 본문(§4). 캐시를 먼저 보고 미스일 때만 upstream(detailCommon2 1콜) 후 저장한다(히트면 upstream
     * 무소모 — AC-19). 오류는 예외로 전파되므로 캐시에 남지 않는다.
     * 갤러리·운영정보·이용안내·좋아요 수는 각 필드가 요청될 때 아래 메서드로 따로 조회한다.
     */
    override fun spotDetail(contentId: String, locale: DomainLocale): SpotDetailView {
        spotCachePort.getDetail(contentId, locale)?.let { return it.toView() }

        // DB 히트: Valkey를 충전하고 upstream은 건드리지 않는다(AC-35).
        spotDetailStorePort.findDetail(contentId, locale)?.let { stored ->
            spotCachePort.putDetail(contentId, locale, stored)
            return stored.toView()
        }

        // 양쪽 미스: upstream 조회 후 캐시 충전 + 적재 이벤트 발행(AC-36·AC-39).
        val fetched = tourInfoPort.findDetail(query(contentId, locale))
        spotCachePort.putDetail(contentId, locale, fetched)
        eventPublisher.publish(SpotDetailFetched(contentId, locale, fetched))
        return fetched.toView()
    }

    override fun spotGallery(contentId: String, locale: DomainLocale): List<SpotImageView> {
        spotCachePort.getGallery(contentId, locale)?.let { return it.map { image -> image.toView() } }

        spotDetailStorePort.findGallery(contentId, locale)?.let { stored ->
            spotCachePort.putGallery(contentId, locale, stored)
            return stored.map { it.toView() }
        }

        val fetched = tourInfoPort.findGallery(query(contentId, locale))
        spotCachePort.putGallery(contentId, locale, fetched)
        eventPublisher.publish(SpotGalleryFetched(contentId, locale, fetched))
        return fetched.map { it.toView() }
    }

    override fun spotIntro(contentId: String, category: SpotCategory, locale: DomainLocale): SpotIntroView? {
        // 히트 판정은 래퍼(non-null)로 한다 — 값 자체는 정상적으로 null일 수 있어, value로 판정하면
        // "값 없음"이 매번 캐시 미스로 취급돼 upstream을 계속 두드리게 된다.
        val cached = spotCachePort.getIntro(contentId, locale, category)
        if (cached != null) return cached.value?.toView()

        // DB도 같은 이유로 래퍼를 쓴다 — "적재됐으나 값 없음"을 미스로 오판하면 upstream을 계속 친다.
        val stored = spotDetailStorePort.findIntro(contentId, locale, category)
        if (stored != null) {
            spotCachePort.putIntro(contentId, locale, category, stored.value)
            return stored.value?.toView()
        }

        val intro = tourInfoPort.findIntro(query(contentId, locale), category)
        spotCachePort.putIntro(contentId, locale, category, intro)
        eventPublisher.publish(SpotIntroFetched(contentId, locale, category, intro))
        return intro?.toView()
    }

    override fun spotInfoItems(
        contentId: String,
        category: SpotCategory,
        locale: DomainLocale,
    ): List<SpotInfoItemView> {
        spotCachePort.getInfoItems(contentId, locale, category)
            ?.let { return it.map { item -> SpotInfoItemView(item.name, item.text) } }

        spotDetailStorePort.findInfoItems(contentId, locale, category)?.let { stored ->
            spotCachePort.putInfoItems(contentId, locale, category, stored)
            return stored.map { SpotInfoItemView(it.name, it.text) }
        }

        val fetched = tourInfoPort.findInfoItems(query(contentId, locale), category)
        spotCachePort.putInfoItems(contentId, locale, category, fetched)
        eventPublisher.publish(SpotInfoItemsFetched(contentId, locale, category, fetched))
        return fetched.map { SpotInfoItemView(it.name, it.text) }
    }

    /**
     * 좋아요 수는 캐시하지 않고 매번 집계한다(AC-14).
     * **3단 폴백 경로에 넣지 않는다**(AC-38) — spot_reaction 기반이라 TourInfoPort와 완전히 다른
     * 포트이며, write-behind는 upstream 파생 데이터만 다룬다.
     */
    override fun spotLikeCount(contentId: String, locale: DomainLocale): Long =
        spotQueryPort.countLikes(contentId, locale)

    private fun query(contentId: String, locale: DomainLocale) =
        SpotDetailQuery(contentId = contentId, locale = locale)

    private fun validatedCenter(latitude: Double, longitude: Double): Coordinates =
        try {
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

    private fun validateCategory(category: SpotCategory, locale: DomainLocale) {
        if (!category.isAvailableIn(locale)) {
            throw CategoryNotAvailableForLocaleException("$category 는 현재 언어에서 제공되지 않습니다.")
        }
    }

    private fun SpotDetail.toView(): SpotDetailView = SpotDetailView(
        id = id,
        title = title,
        category = category,
        thumbnailImageUrl = thumbnailImageUrl,
        overview = overview,
        address = address,
        homepage = homepage,
        tel = tel,
        copyright = copyright,
        attributionText = attributionText,
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

    /**
     * 읽기 DTO → 응답 뷰. [attributionText]는 copyright에서 파생되므로 여기서 도출한다 —
     * 적재하지 않는 이유이기도 하다(컬럼으로 두면 둘이 어긋날 수 있다).
     * 거리는 DB가 계산한 값을 그대로 쓴다.
     */
    private fun SpotSearchResult.toView(): SpotView = SpotView(
        id = contentId,
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
