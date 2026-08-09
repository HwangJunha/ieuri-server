package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.common.pagination.keysetPageOf
import org.around.ieuri.common.pagination.map
import org.around.ieuri.tourism.application.exception.CategoryNotAvailableForLocaleException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.SpotDiscoveryUseCase
import org.around.ieuri.tourism.application.port.out.query.FestivalQueryPort
import org.around.ieuri.tourism.application.port.out.query.OngoingFestivalSearch
import org.around.ieuri.tourism.application.port.out.query.PopularSpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.PopularSpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotSearchPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchQuery
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.query.FestivalView
import org.around.ieuri.tourism.application.query.OngoingFestivalsQuery
import org.around.ieuri.tourism.application.query.PopularSpotView
import org.around.ieuri.tourism.application.query.PopularSpotsQuery
import org.around.ieuri.tourism.application.query.SearchSpotsQuery
import org.around.ieuri.tourism.application.query.SearchUserError
import org.around.ieuri.tourism.application.query.SpotSearchResultView
import org.around.ieuri.tourism.application.query.SpotSuggestionView
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DistanceCursor
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.LikeCountCursor
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * spot 발견 조회 구현(SPEC-0013). 네 조회 모두 읽기 전용이며 도메인 애그리거트를 거치지 않는다 —
 * QueryDSL·생 SQL 프로젝션이 read DTO를 직접 만든다.
 *
 * 공통 규약 셋:
 *  - 좌표·반경 검증은 `spots`와 같은 예외를 쓴다. 같은 인자에 다른 코드를 두지 않는다.
 *  - 커서 페이지는 요청한 개수보다 **한 건 더 읽어** 다음 페이지 존재를 판정한다. 별도 카운트로
 *    판정하면 조건이 갈려 hasNextPage와 어긋난다.
 *  - **전체 건수는 검색만 센다.** 검색 페이지가 결과 수를 표시하기 때문이며, 홈의 축제·인기
 *    캐러셀은 숫자를 보여주지 않아 매 요청 집계 쿼리를 한 번 더 도는 값을 만들지 않는다
 *    (likedSpots와 같은 규약).
 *  - 정렬 키에 spot id를 tie-breaker로 붙여 전순서를 만든다 — 동점이 흔해 이것이 없으면
 *    페이지 간 항목이 중복·누락된다(R7).
 */
@Service
class SpotDiscoveryService(
    private val spotSearchPort: SpotSearchPort,
    private val festivalQueryPort: FestivalQueryPort,
    private val popularSpotQueryPort: PopularSpotQueryPort,
    private val policy: SpotQueryPolicy,
    private val clock: Clock,
) : SpotDiscoveryUseCase {

    /**
     * 검색(R1-R8). **질의 검증이 포트 호출보다 먼저다**(AC-8.1) — 검증을 어댑터로 내리면 빈 질의가
     * 검색 엔진까지 도달해 전체 코퍼스를 훑는다.
     *
     * 질의 오류만 userErrors로 돌려주고 좌표·반경 오류는 예외로 던진다. 전자는 사용자가 입력창에서
     * 고칠 수 있는 값이고, 후자는 클라이언트가 잘못 보낸 것이라 화면에 띄울 메시지가 아니다.
     */
    override fun searchSpots(query: SearchSpotsQuery, locale: DomainLocale): SpotSearchResultView {
        val center = validatedCenter(query.latitude, query.longitude)
        validateRadius(query.radiusMeters)
        query.category?.let { validateCategory(it, locale) }

        val text = query.query.trim()
        val page = query.page.coerceAtLeast(1)
        validateSearchInput(text, page)?.let { return emptyResult(listOf(it), page) }

        val size = query.size.coerceIn(1, policy.maxSearchPageSize)
        val search = SpotSearchQuery(
            text = text,
            language = locale,
            center = center,
            radiusMeters = query.radiusMeters,
            category = query.category,
            limit = size,
            offset = (page - 1) * size,
        )

        // 포트는 size + 1건까지 돌려준다. 초과분은 "다음 페이지가 있다"는 신호일 뿐 싣지 않는다.
        val hits = spotSearchPort.search(search)
        return SpotSearchResultView(
            userErrors = emptyList(),
            items = hits.take(size).map { it.spot.toView() },
            page = page,
            hasNext = hits.size > size,
            totalCount = spotSearchPort.count(search),
        )
    }

    /**
     * 자동완성(R9). 빈 질의는 오류가 아니라 빈 목록이다(AC-9.4) — 입력창이 비어 있는 것은 정상
     * 상태이며, 이때 포트를 호출하면 반경 내 전체 spot을 제목순으로 긁어 온다.
     *
     * 길이 상한은 걸지 않는다 — 검색과 달리 거절할 이유가 없고, 긴 접두사는 자연히 0건이 된다.
     */
    override fun spotSearchSuggestions(
        query: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        limit: Int,
        locale: DomainLocale,
    ): List<SpotSuggestionView> {
        val center = validatedCenter(latitude, longitude)
        validateRadius(radiusMeters)

        val prefix = query.trim()
        if (prefix.isEmpty()) return emptyList()

        return spotSearchPort.suggest(
            prefix = prefix,
            language = locale,
            center = center,
            radiusMeters = radiusMeters,
            limit = limit.coerceIn(1, policy.maxSuggestionCount),
        ).map { SpotSuggestionView(id = it.spotId, title = it.title, category = it.category) }
    }

    /**
     * 진행 중 축제(R17). '오늘'을 시계에서 읽어 포트에 넘긴다 — 어댑터가 `CURRENT_DATE`를 직접
     * 쓰면 경계 판정을 단위 테스트로 고정할 수 없다(AC-17.1~17.3).
     *
     * **날짜는 KST로 뽑는다.** 공유 Clock이 UTC라 그대로 `LocalDate`를 만들면 09시간 어긋나,
     * 한국 시간 자정~09시 사이에 어제 끝난 축제가 진행 중으로 남는다. 대상 데이터가 전부
     * 한국 행사이므로 기준 시간대도 한국이어야 한다.
     */
    override fun ongoingFestivals(
        query: OngoingFestivalsQuery,
        locale: DomainLocale,
    ): CursorPage<FestivalView> {
        val center = validatedCenter(query.latitude, query.longitude)
        validateRadius(query.radiusMeters)

        val first = query.first.coerceIn(1, policy.maxSearchPageSize)
        val search = OngoingFestivalSearch(
            language = locale,
            center = center,
            radiusMeters = query.radiusMeters,
            asOf = LocalDate.ofInstant(clock.instant(), FESTIVAL_ZONE),
            limit = first,
            after = query.after?.let { DistanceCursor.decode(it) },
        )

        return keysetPageOf(
            rows = festivalQueryPort.findOngoing(search),
            first = first,
            after = query.after,
        ) { DistanceCursor(it.distanceMeters, it.id).encode() }
    }

    /**
     * 인기 spot(R18). 좋아요 하한은 정책값이며 클라이언트가 고를 수 없다 — 순위의 신뢰도 기준이라
     * 호출자마다 다르면 "인기"의 의미가 흔들린다.
     */
    override fun popularSpots(
        query: PopularSpotsQuery,
        locale: DomainLocale,
    ): CursorPage<PopularSpotView> {
        val center = validatedCenter(query.latitude, query.longitude)
        validateRadius(query.radiusMeters)
        query.category?.let { validateCategory(it, locale) }

        val first = query.first.coerceIn(1, policy.maxSearchPageSize)
        val search = PopularSpotSearch(
            language = locale,
            center = center,
            radiusMeters = query.radiusMeters,
            category = query.category,
            minLikeCount = policy.minPopularLikeCount,
            limit = first,
            after = query.after?.let { LikeCountCursor.decode(it) },
        )

        return keysetPageOf(
            rows = popularSpotQueryPort.findPopular(search),
            first = first,
            after = query.after,
        ) { LikeCountCursor(it.likeCount, it.spot.id).encode() }
    }

    /** 검증 실패 시의 응답. connection이 non-null이라 빈 값이라도 있어야 한다(POL-015). */
    private fun emptyResult(errors: List<SearchUserError>, page: Int) = SpotSearchResultView(
        userErrors = errors,
        items = emptyList(),
        page = page,
        hasNext = false,
        totalCount = 0,
    )

    /**
     * 검색 입력 검증(R8). 트림 후 비었으면 EMPTY_QUERY, 길이 상한을 넘으면 QUERY_TOO_LONG,
     * 페이지가 너무 깊으면 PAGE_TOO_DEEP이다.
     *
     * **길이는 트림 후를 센다** — 앞뒤 공백으로 상한을 넘기는 것은 사용자의 의도가 아니다.
     *
     * 페이지 상한을 두는 이유는 OFFSET 비용이 깊이에 비례해 커지기 때문이다. 검색 엔진들이
     * 공통으로 두는 제약이며(Elasticsearch의 기본 창이 10,000건), 그 지점에서 필요한 것은
     * 더 깊은 페이지가 아니라 좁은 질의다.
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

    /** attributionText는 copyright에서 파생된다 — 적재하지 않는 이유이기도 하다(둘이 어긋날 수 있다). */
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

    private companion object {
        const val FIELD_QUERY = "query"
        const val FIELD_PAGE = "page"

        /** 행사 기간 판정의 기준 시간대. TourAPI가 적재하는 축제가 전부 한국 행사다. */
        val FESTIVAL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
