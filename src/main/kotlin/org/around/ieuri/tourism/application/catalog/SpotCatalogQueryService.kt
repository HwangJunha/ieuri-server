package org.around.ieuri.tourism.application.catalog

import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.OffsetPage
import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogQueryUseCase
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.MapViewportQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.MapViewportView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotSummaryView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.exception.BboxTooLargeException
import org.around.ieuri.tourism.application.catalog.provided.exception.EmptyQueryException
import org.around.ieuri.tourism.application.catalog.provided.exception.InvalidZoomException
import org.around.ieuri.tourism.application.catalog.provided.exception.QueryTooLongException
import org.around.ieuri.tourism.application.catalog.required.CatalogEventPublisherPort
import org.around.ieuri.tourism.application.catalog.required.LikedSpotSearchPort
import org.around.ieuri.tourism.application.catalog.required.MapQueryPort
import org.around.ieuri.tourism.application.catalog.required.SpotQueryPort
import org.around.ieuri.tourism.application.catalog.required.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.catalog.required.dto.LikedSpotsSearch
import org.around.ieuri.tourism.application.catalog.required.dto.MapViewportSearch
import org.around.ieuri.tourism.application.catalog.required.dto.NearbySpotSearch
import org.around.ieuri.tourism.application.catalog.required.dto.SpotBasics
import org.around.ieuri.tourism.application.congestion.provided.CongestionQueryUseCase
import org.around.ieuri.tourism.domain.catalog.ReactionType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 목록 조회 구현(CQRS 읽기). 적재된 spot 테이블만 보며 TourAPI를 호출하지 않는다.
 * - 뷰포트의 문화행사는 혼잡도 캐시에 있는 값만 쓴다.
 */
@Service
@Transactional(readOnly = true)
class SpotCatalogQueryService(
    private val spotQueryPort: SpotQueryPort,
    private val spotSpatialQueryPort: SpotSpatialQueryPort,
    private val likedSpotSearchPort: LikedSpotSearchPort,
    private val mapQueryPort: MapQueryPort,
    private val congestionQueryUseCase: CongestionQueryUseCase,
    private val eventPublisher: CatalogEventPublisherPort,
    private val policy: SpotQueryPolicy,
    private val viewportPolicy: MapViewportPolicy,
) : SpotCatalogQueryUseCase {

    /** 주변 관광지 한 페이지. 거리 오름차순 + id 타이브레이크로 정렬된다. */
    override fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): OffsetPage<SpotView> {
        // 좌표 조회 사실을 남긴다(SPEC-0032 R19). 좌표는 넘기지 않고, 비로그인은 발행하지 않는다.
        memberId?.let { eventPublisher.publish(LocationAccessed(it, "spots")) }
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

        return OffsetPage.of(results, page = page, size = size, totalCount = totalCount)
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

    /** 카탈로그 한 행을 읽는다. [languageCode]에서 해소되지 않으면 null이다 — 다른 언어로 대신하지 않는다. */
    override fun spotSummary(catalogId: Long, languageCode: String): SpotSummaryView? = spotQueryPort.findSpotBasics(catalogId, DomainLocale.fromLanguageTag(languageCode))?.toSummary(catalogId)

    /** 카탈로그 여러 행을 읽는다. [languageCode]에서 해소되지 않는 id는 결과에 키가 없다(SPEC-0029 R-24). */
    override fun spotSummaries(catalogIds: List<Long>, languageCode: String): Map<Long, SpotSummaryView> = spotQueryPort.findSpotBasicsIn(catalogIds, DomainLocale.fromLanguageTag(languageCode))
        .mapValues { (catalogId, basics) -> basics.toSummary(catalogId) }

    /** 좋아요 수. 캐시하지 않고 매번 집계한다. */
    override fun spotLikeCount(catalogId: Long, locale: DomainLocale): Long = spotQueryPort.countLikes(catalogId, locale)

    /** 최신 반응. 캐시하지 않는다. */
    override fun spotViewerReaction(catalogId: Long, memberId: Long, locale: DomainLocale): ReactionType? = spotQueryPort.findReaction(catalogId, memberId, locale)

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

    private fun SpotBasics.toSummary(catalogId: Long) = SpotSummaryView(
        id = catalogId,
        title = title,
        thumbnailImageUrl = thumbnailImageUrl,
        categoryCode = category.code,
        longitude = longitude,
        latitude = latitude,
    )
}
