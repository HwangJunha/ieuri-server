package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.exception.CategoryNotAvailableForLocaleException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.port.out.client.NearbySpotData
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.query.SpotPageView
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.Spot
import org.around.ieuri.tourism.domain.vo.Coordinates
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
    private val policy: SpotQueryPolicy,
) : SpotQueryUseCase {

    override fun spots(query: SpotsQuery, locale: DomainLocale): SpotPageView {
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

        // DTO→Domain(거리 채움) 후 거리 오름차순 정렬(동률이면 id). 정렬은 계약(POL-019).
        val items = result.spots
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
