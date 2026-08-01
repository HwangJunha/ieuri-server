package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.exception.CategoryNotAvailableForLocaleException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.port.out.client.NearbySpotData
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.cache.SpotCachePort
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.query.SpotDetailView
import org.around.ieuri.tourism.application.query.SpotImageView
import org.around.ieuri.tourism.application.query.SpotInfoItemView
import org.around.ieuri.tourism.application.query.SpotIntroView
import org.around.ieuri.tourism.application.query.SpotPageView
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.Spot
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotIntro
import org.around.ieuri.tourism.domain.vo.Distance
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.stereotype.Service

/**
 * 관광지 조회 유스케이스 구현(CQRS 읽기). 파이프라인:
 *  - 좌표/반경/카테고리 검증 : 여기(application)
 *  - TourAPI 호출·필터·정규화 : TourInfoPort 뒤 어댑터
 *  - 거리 계산·정렬(거리 ASC) : 여기(application) — 정렬은 계약(POL-019)
 *
 * TourAPI가 pageNo 오프셋만 지원하고 라이브 데이터라, 커서 대신 **오프셋 한 페이지**를 그대로 서빙한다.
 * 클라이언트가 한 페이지를 스택에 쌓고 소진 시 page+1을 조회한다(서버가 페이지 병합·재조립을 하지 않는다).
 */
@Service
class SpotQueryService(
    private val tourInfoPort: TourInfoPort,
    private val spotQueryPort: SpotQueryPort,
    private val spotCachePort: SpotCachePort,
    private val policy: SpotQueryPolicy,
) : SpotQueryUseCase {

    override fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): SpotPageView {
        val center = validatedCenter(query.latitude, query.longitude)
        validateRadius(query.radiusMeters)
        query.category?.let { validateCategory(it, locale) }

        val page = query.page.coerceAtLeast(1)
        val size = query.size.coerceIn(1, policy.maxPageSize)

        val result = tourInfoPort.findNearby(
            NearbySpotQuery(
                locale = locale,
                center = center,
                radiusMeters = query.radiusMeters,
                category = query.category,
                pageNo = page,
                numOfRows = size,
            ),
        )

        // 로그인 회원이 이미 반응(좋아요/패스)한 관광지 제외 — 중복 노출 방지. 비로그인은 제외 없음.
        val reacted = memberId?.let {
            spotQueryPort.findReactedContentIds(it, result.spots.map { s -> s.id }, locale)
        }.orEmpty()

        // DTO→Domain(거리 채움) 후 거리 오름차순 정렬(동률이면 id). 정렬은 계약(POL-019).
        val items = result.spots
            .filter { it.id !in reacted }
            .map { it.toSpot(center) }
            .sortedWith(compareBy({ it.distance.meters }, { it.id }))
            .map { it.toView() }

        return SpotPageView(
            items = items,
            page = page,
            hasNext = result.hasMoreUpstreamPages,
            totalCount = result.totalCount,
        )
    }

    override fun availableCategories(locale: DomainLocale): List<SpotCategory> =
        SpotCategory.availableIn(locale)

    /**
     * 상세 본문(§4). 캐시를 먼저 보고 미스일 때만 upstream(detailCommon2 1콜) 후 저장한다(히트면 upstream
     * 무소모 — AC-19). 오류는 예외로 전파되므로 캐시에 남지 않는다.
     * 갤러리·운영정보·이용안내·좋아요 수는 각 필드가 요청될 때 아래 메서드로 따로 조회한다.
     */
    override fun spotDetail(contentId: String, locale: DomainLocale): SpotDetailView =
        (
            spotCachePort.getDetail(contentId, locale)
                ?: tourInfoPort.findDetail(query(contentId, locale))
                    .also { spotCachePort.putDetail(contentId, locale, it) }
            ).toView()

    override fun spotGallery(contentId: String, locale: DomainLocale): List<SpotImageView> =
        (
            spotCachePort.getGallery(contentId, locale)
                ?: tourInfoPort.findGallery(query(contentId, locale))
                    .also { spotCachePort.putGallery(contentId, locale, it) }
            ).map { it.toView() }

    override fun spotIntro(contentId: String, category: SpotCategory, locale: DomainLocale): SpotIntroView? {
        // 히트 판정은 래퍼(non-null)로 한다 — 값 자체는 정상적으로 null일 수 있어, value로 판정하면
        // "값 없음"이 매번 캐시 미스로 취급돼 upstream을 계속 두드리게 된다.
        val cached = spotCachePort.getIntro(contentId, locale, category)
        if (cached != null) return cached.value?.toView()

        val intro = tourInfoPort.findIntro(query(contentId, locale), category)
        spotCachePort.putIntro(contentId, locale, category, intro)
        return intro?.toView()
    }

    override fun spotInfoItems(
        contentId: String,
        category: SpotCategory,
        locale: DomainLocale,
    ): List<SpotInfoItemView> =
        (
            spotCachePort.getInfoItems(contentId, locale, category)
                ?: tourInfoPort.findInfoItems(query(contentId, locale), category)
                    .also { spotCachePort.putInfoItems(contentId, locale, category, it) }
            ).map { SpotInfoItemView(name = it.name, text = it.text) }

    /** 좋아요 수는 캐시하지 않고 매번 집계한다(§5·AC-14). */
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

    /** 포트 중립 DTO → 도메인 Spot. 거리(도메인 로직)를 사용자 좌표 기준으로 채운다(DTO→Domain). */
    private fun NearbySpotData.toSpot(center: Coordinates): Spot = Spot(
        id = id,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        coordinates = coordinates,
        distance = Distance.between(center, coordinates),
        category = category,
        copyright = copyright,
        attributionText = copyright.attributionText,
    )

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

    private fun Spot.toView(): SpotView = SpotView(
        id = id,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        distanceMeters = distance.meters,
        category = category,
        copyright = copyright,
        attributionText = attributionText,
        mapX = coordinates.longitude,
        mapY = coordinates.latitude,
    )
}
