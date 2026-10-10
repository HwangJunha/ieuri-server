package org.around.ieuri.tourism.application.catalog

import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.OffsetPage
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.catalog.provided.SpotDiscoveryUseCase
import org.around.ieuri.tourism.application.catalog.provided.dto.FestivalView
import org.around.ieuri.tourism.application.catalog.provided.dto.OngoingFestivalsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.PopularSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.PopularSpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.SearchSpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.SearchUserError
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotSearchResultView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotSuggestionView
import org.around.ieuri.tourism.application.catalog.required.CatalogEventPublisherPort
import org.around.ieuri.tourism.application.catalog.required.FestivalQueryPort
import org.around.ieuri.tourism.application.catalog.required.PopularSpotQueryPort
import org.around.ieuri.tourism.application.catalog.required.SpotSearchPort
import org.around.ieuri.tourism.application.catalog.required.dto.OngoingFestivalSearch
import org.around.ieuri.tourism.application.catalog.required.dto.PopularSpotSearch
import org.around.ieuri.tourism.application.catalog.required.dto.SpotSearchQuery
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * spot 발견 조회 구현(SPEC-0013).
 * - 좌표·반경 검증은 `spots`와 같은 예외를 쓴다.
 * - 전체 건수는 검색만 센다.
 * - 정렬 키에 spot id를 tie-breaker로 붙인다(R7).
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class SpotDiscoveryService(
    private val spotSearchPort: SpotSearchPort,
    private val festivalQueryPort: FestivalQueryPort,
    private val popularSpotQueryPort: PopularSpotQueryPort,
    private val policy: SpotQueryPolicy,
    private val clock: Clock,
    private val eventPublisher: CatalogEventPublisherPort,
) : SpotDiscoveryUseCase {

    /**
     * 검색(R1-R8). 질의 검증이 포트 호출보다 먼저다(AC-8.1).
     * - 질의 오류는 userErrors로, 좌표·반경 오류는 예외로 던진다.
     */
    override fun searchSpots(query: SearchSpotsQuery, locale: DomainLocale, memberId: Long?): SpotSearchResultView {
        auditLocationAccess(memberId, "searchSpots")
        val center = coordinatesOf(query.latitude, query.longitude)
        requireRadiusWithin(query.radiusMeters, policy.maxRadiusMeters)

        val text = query.query.trim()
        val page = query.page.coerceAtLeast(1)
        validateSearchInput(text, page)?.let { return emptyResult(listOf(it), page) }

        val size = query.size.coerceIn(1, policy.maxSearchPageSize)
        val search = SpotSearchQuery(
            text = text,
            language = locale,
            center = center,
            radiusMeters = query.radiusMeters,
            categories = query.categories,
            limit = size,
            offset = (page - 1) * size,
        )

        val totalCount = spotSearchPort.count(search)
        return SpotSearchResultView(
            userErrors = emptyList(),
            results = OffsetPage.of(spotSearchPort.search(search), page = page, size = size, totalCount = totalCount),
        )
    }

    /**
     * 자동완성(R9).
     * - 트림 후 빈 질의는 오류가 아니라 빈 목록이다(AC-9.4).
     * - 길이 상한은 걸지 않는다.
     */
    override fun spotSearchSuggestions(
        query: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        limit: Int,
        locale: DomainLocale,
        memberId: Long?,
    ): List<SpotSuggestionView> {
        auditLocationAccess(memberId, "spotSearchSuggestions")
        val center = coordinatesOf(latitude, longitude)
        requireRadiusWithin(radiusMeters, policy.maxRadiusMeters)

        val prefix = query.trim()
        if (prefix.isEmpty()) return emptyList()

        return spotSearchPort.suggest(
            prefix = prefix,
            language = locale,
            center = center,
            radiusMeters = radiusMeters,
            limit = limit.coerceIn(1, policy.maxSuggestionCount),
        )
    }

    /**
     * 진행 중 축제(R17). '오늘'을 시계에서 읽어 포트에 넘긴다.
     * - 날짜는 KST로 뽑는다 — 공유 Clock은 UTC다.
     */
    override fun ongoingFestivals(
        query: OngoingFestivalsQuery,
        locale: DomainLocale,
        memberId: Long?,
    ): CursorPage<FestivalView> {
        auditLocationAccess(memberId, "ongoingFestivals")
        val center = coordinatesOf(query.latitude, query.longitude)
        requireRadiusWithin(query.radiusMeters, policy.maxRadiusMeters)

        val first = query.first.coerceIn(1, policy.maxSearchPageSize)
        val search = OngoingFestivalSearch(
            language = locale,
            center = center,
            radiusMeters = query.radiusMeters,
            asOf = LocalDate.ofInstant(clock.instant(), FESTIVAL_ZONE),
            limit = first,
            after = query.after,
        )

        return festivalQueryPort.findOngoing(search)
    }

    /** 인기 spot(R18). 좋아요 하한은 정책값이며 클라이언트가 고를 수 없다. */
    override fun popularSpots(
        query: PopularSpotsQuery,
        locale: DomainLocale,
        memberId: Long?,
    ): CursorPage<PopularSpotView> {
        auditLocationAccess(memberId, "popularSpots")
        val center = coordinatesOf(query.latitude, query.longitude)
        requireRadiusWithin(query.radiusMeters, policy.maxRadiusMeters)

        val first = query.first.coerceIn(1, policy.maxSearchPageSize)
        val search = PopularSpotSearch(
            language = locale,
            center = center,
            radiusMeters = query.radiusMeters,
            categories = query.categories,
            minLikeCount = policy.minPopularLikeCount,
            limit = first,
            after = query.after,
        )

        return popularSpotQueryPort.findPopular(search)
    }

    /** 검증 실패 시의 응답. connection이 non-null이라 빈 값이라도 있어야 한다(POL-015). */
    private fun emptyResult(errors: List<SearchUserError>, page: Int) = SpotSearchResultView(userErrors = errors, results = OffsetPage.empty(page))

    /**
     * 검색 입력 검증(R8). 트림 후 비었으면 EMPTY_QUERY, 길이 상한을 넘으면 QUERY_TOO_LONG,
     * 페이지 상한을 넘으면 PAGE_TOO_DEEP이다.
     * - 길이는 트림 후를 센다.
     */
    private fun validateSearchInput(trimmed: String, page: Int): SearchUserError? = when {
        trimmed.isEmpty() ->
            SearchUserError("EMPTY_QUERY", "검색어를 입력해 주세요.", FIELD_QUERY)

        trimmed.length > policy.maxSearchQueryLength ->
            SearchUserError(
                "QUERY_TOO_LONG",
                "검색어는 최대 ${policy.maxSearchQueryLength}자까지 입력할 수 있습니다.",
                FIELD_QUERY,
            )

        page > policy.maxSearchPage ->
            SearchUserError(
                "PAGE_TOO_DEEP",
                "검색 결과는 ${policy.maxSearchPage}페이지까지 볼 수 있습니다. 검색어를 좁혀 주세요.",
                FIELD_PAGE,
            )

        else -> null
    }

    private companion object {
        const val FIELD_QUERY = "query"
        const val FIELD_PAGE = "page"

        /** 행사 기간 판정의 기준 시간대. */
        val FESTIVAL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }

    /** 좌표를 받는 연산의 조회 사실을 남긴다(SPEC-0032 R19). 좌표는 넘기지 않고, 비로그인은 발행하지 않는다. */
    private fun auditLocationAccess(memberId: Long?, operation: String) {
        memberId?.let { eventPublisher.publish(LocationAccessed(it, operation)) }
    }
}
