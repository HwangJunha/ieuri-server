package org.around.ieuri.tourism.application.catalog

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogQueryUseCase
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.MapViewportQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.MapViewportView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotPageView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.exception.BboxTooLargeException
import org.around.ieuri.tourism.application.catalog.provided.exception.EmptyQueryException
import org.around.ieuri.tourism.application.catalog.provided.exception.InvalidZoomException
import org.around.ieuri.tourism.application.catalog.provided.exception.QueryTooLongException
import org.around.ieuri.tourism.application.catalog.required.LikedSpotSearchPort
import org.around.ieuri.tourism.application.catalog.required.LikedSpotsSearch
import org.around.ieuri.tourism.application.catalog.required.MapQueryPort
import org.around.ieuri.tourism.application.catalog.required.MapViewportSearch
import org.around.ieuri.tourism.application.catalog.required.NearbySpotSearch
import org.around.ieuri.tourism.application.catalog.required.SpotQueryPort
import org.around.ieuri.tourism.application.catalog.required.SpotSearchResult
import org.around.ieuri.tourism.application.catalog.required.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.congestion.provided.CongestionQueryUseCase
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 목록 조회 구현(CQRS 읽기). 적재된 spot 테이블만 보며 TourAPI를 호출하지 않는다.
 * - 뷰포트의 문화행사는 혼잡도 캐시에 있는 값만 쓴다.
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class SpotCatalogQueryService(
    private val spotQueryPort: SpotQueryPort,
    private val spotSpatialQueryPort: SpotSpatialQueryPort,
    private val likedSpotSearchPort: LikedSpotSearchPort,
    private val mapQueryPort: MapQueryPort,
    private val congestionQueryUseCase: CongestionQueryUseCase,
    private val policy: SpotQueryPolicy,
    private val viewportPolicy: MapViewportPolicy,
) : SpotCatalogQueryUseCase {

    /** 주변 관광지 한 페이지. 거리 오름차순 + id 타이브레이크로 정렬된다. */
    override fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): SpotPageView {
        val center = coordinatesOf(query.latitude, query.longitude)
        requireRadiusWithin(query.radiusMeters, policy.maxRadiusMeters)

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

    /** 뷰포트 마커. 문화행사는 캐시된 값만 쓰고 업스트림을 부르지 않는다. */
    override fun mapViewport(query: MapViewportQuery, locale: DomainLocale): MapViewportView {
        if (!viewportPolicy.isValidZoom(query.zoom)) {
            throw InvalidZoomException("지도 확대 수준이 올바르지 않습니다.")
        }
        if (query.bounds.areaInSquareDegrees > viewportPolicy.maxAreaInSquareDegrees) {
            throw BboxTooLargeException("지도를 확대해 주세요.")
        }

        val events = congestionQueryUseCase.cachedEventsWithin(query.bounds)
        val eventsTruncated = events.size > viewportPolicy.maxEventMarkers

        val spots = mapQueryPort.findSpotsWithin(
            MapViewportSearch(
                language = locale,
                bounds = query.bounds,
                categories = query.categories,
                // 상한 + 1건을 받아 초과 여부만 판정한다(R3).
                limit = viewportPolicy.maxSpotMarkers + 1,
            ),
        )
        return MapViewportView(
            spots = spots.take(viewportPolicy.maxSpotMarkers),
            events = events.take(viewportPolicy.maxEventMarkers),
            truncated = spots.size > viewportPolicy.maxSpotMarkers || eventsTruncated,
        )
    }

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
