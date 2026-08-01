package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.exception.CategoryNotAvailableForLocaleException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.port.out.client.NearbySpotData
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.query.SpotReactionQueryPort
import org.around.ieuri.tourism.application.query.SpotDetailView
import org.around.ieuri.tourism.application.query.SpotImageView
import org.around.ieuri.tourism.application.query.SpotPageView
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.Spot
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.SpotImage
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
    private val spotReactionQueryPort: SpotReactionQueryPort,
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
            spotReactionQueryPort.findReactedContentIds(it, result.spots.map { s -> s.id }, locale)
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
     * 상세 조회. tourInfoPort는 캐시 데코레이터가 앞선 상태라(§4) 캐시 히트면 upstream 무소모. TourAPI 파생 필드는
     * 캐시 값이나, likeCount는 캐시와 무관하게 매번 실시간 집계해 얹는다(§5·AC-14).
     */
    override fun spotDetail(contentId: String, locale: DomainLocale): SpotDetailView {
        val detail = tourInfoPort.findDetail(SpotDetailQuery(contentId = contentId, locale = locale))
        val likeCount = spotReactionQueryPort.countLikes(contentId, locale)
        return detail.withLikeCount(likeCount).toView()
    }

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
        gallery = gallery.map { it.toView() },
        likeCount = likeCount,
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
