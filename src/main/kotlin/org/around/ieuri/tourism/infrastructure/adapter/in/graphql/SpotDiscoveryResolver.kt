package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.common.graphql.tokenPresentation
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.application.port.`in`.query.SpotDiscoveryUseCase
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.query.FestivalView
import org.around.ieuri.tourism.application.query.OngoingFestivalsQuery
import org.around.ieuri.tourism.application.query.PopularSpotsQuery
import org.around.ieuri.tourism.application.query.SearchSpotsQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toCategorySelection
import org.around.ieuri.tourism.infrastructure.locale.domainLocale
import org.around.ieuri.common.graphql.generated.types.CopyrightType as GqlCopyrightType
import org.around.ieuri.common.graphql.generated.types.Festival as GqlFestival
import org.around.ieuri.common.graphql.generated.types.FestivalConnection as GqlFestivalConnection
import org.around.ieuri.common.graphql.generated.types.FestivalEdge as GqlFestivalEdge
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.PopularSpotConnection as GqlPopularSpotConnection
import org.around.ieuri.common.graphql.generated.types.PopularSpotEdge as GqlPopularSpotEdge
import org.around.ieuri.common.graphql.generated.types.Spot as GqlSpot
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory
import org.around.ieuri.common.graphql.generated.types.SpotSearchConnection as GqlSpotSearchConnection
import org.around.ieuri.common.graphql.generated.types.SpotSearchPayload as GqlSpotSearchPayload
import org.around.ieuri.common.graphql.generated.types.SpotSuggestion as GqlSpotSuggestion
import org.around.ieuri.common.graphql.generated.types.UserError as GqlUserError

/**
 * spot 발견 조회 리졸버(SPEC-0013). 언어는 Accept-Language 헤더에서 읽는다. 네 쿼리 모두 비로그인 허용이다.
 * - 좌표·반경·카테고리 오류는 최상위 errors[]로, 검색어 오류만 payload의 userErrors로 나간다.
 * - 커넥션 타입 이름은 `Connection`으로 끝나야 한다.
 */
@DgsComponent
class SpotDiscoveryResolver(
    private val spotDiscoveryUseCase: SpotDiscoveryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
    private val eventPublisher: TourismEventPublisherPort,
) {

    /**
     * 좌표를 받는 연산의 조회 사실을 남긴다(SPEC-0032 R19). 좌표는 넘기지 않는다.
     * - 만료 토큰은 비로그인으로 흘리지 않고 오류다 — 기록 없이 지나가지 않게 한다(POL-057).
     */
    private fun auditLocationAccess(dfe: DgsDataFetchingEnvironment, operation: String) {
        // 비로그인은 남길 "누구"가 없어 발행하지 않는다.
        val memberId = currentMemberProvider.optionalMemberId(dfe.tokenPresentation()) ?: return
        eventPublisher.publish(LocationAccessed(memberId, operation))
    }

    @DgsQuery
    fun searchSpots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument query: String,
        @InputArgument latitude: Double,
        @InputArgument longitude: Double,
        @InputArgument radiusMeters: Int?,
        @InputArgument categories: List<String>?,
        @InputArgument page: Int?,
        @InputArgument size: Int?,
    ): GqlSpotSearchPayload {
        auditLocationAccess(dfe, "searchSpots")
        val result = spotDiscoveryUseCase.searchSpots(
            SearchSpotsQuery(
                query = query,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                categories = toCategorySelection(categories),
                page = page ?: DEFAULT_PAGE,
                size = size ?: DEFAULT_PAGE_SIZE,
            ),
            locale = dfe.domainLocale(),
        )

        return GqlSpotSearchPayload(
            success = result.success,
            userErrors = result.userErrors.map {
                GqlUserError(code = it.code, message = it.message, field = it.field)
            },
            // 검증 실패에도 connection은 존재하며 비어 있다.
            connection = GqlSpotSearchConnection(
                items = result.items.map { it.toGql() },
                page = result.page,
                hasNext = result.hasNext,
                totalCount = result.totalCount,
            ),
        )
    }

    @DgsQuery
    fun spotSearchSuggestions(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument query: String,
        @InputArgument latitude: Double,
        @InputArgument longitude: Double,
        @InputArgument radiusMeters: Int?,
        @InputArgument limit: Int?,
    ): List<GqlSpotSuggestion> {
        auditLocationAccess(dfe, "spotSearchSuggestions")
        return spotDiscoveryUseCase.spotSearchSuggestions(
            query = query,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
            limit = limit ?: DEFAULT_SUGGESTION_LIMIT,
            locale = dfe.domainLocale(),
        ).map {
            GqlSpotSuggestion(
                id = it.id,
                title = it.title,
                category = GqlSpotCategory(code = it.category.code, label = it.category.code, iconUrl = null),
            )
        }
    }

    @DgsQuery
    fun ongoingFestivals(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument latitude: Double,
        @InputArgument longitude: Double,
        @InputArgument radiusMeters: Int?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlFestivalConnection {
        auditLocationAccess(dfe, "ongoingFestivals")
        val page = spotDiscoveryUseCase.ongoingFestivals(
            OngoingFestivalsQuery(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                first = first ?: DEFAULT_PAGE_SIZE,
                after = after,
            ),
            locale = dfe.domainLocale(),
        )
        return GqlFestivalConnection(
            edges = page.edges.map { GqlFestivalEdge(cursor = it.cursor, node = it.node.toGql()) },
            pageInfo = page.toGqlPageInfo(),
        )
    }

    @DgsQuery
    fun popularSpots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument latitude: Double,
        @InputArgument longitude: Double,
        @InputArgument radiusMeters: Int?,
        @InputArgument categories: List<String>?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlPopularSpotConnection {
        auditLocationAccess(dfe, "popularSpots")
        val page = spotDiscoveryUseCase.popularSpots(
            PopularSpotsQuery(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                categories = toCategorySelection(categories),
                first = first ?: DEFAULT_PAGE_SIZE,
                after = after,
            ),
            locale = dfe.domainLocale(),
        )
        return GqlPopularSpotConnection(
            edges = page.edges.map {
                GqlPopularSpotEdge(
                    cursor = it.cursor,
                    node = it.node.spot.toGql(),
                    // likeCount는 순위 근거라 엣지의 값이다.
                    likeCount = it.node.likeCount.toInt(),
                )
            },
            pageInfo = page.toGqlPageInfo(),
        )
    }

    private fun CursorPage<*>.toGqlPageInfo(): GqlPageInfo = GqlPageInfo(
        hasNextPage = pageInfo.hasNextPage,
        hasPreviousPage = pageInfo.hasPreviousPage,
        startCursor = pageInfo.startCursor,
        endCursor = pageInfo.endCursor,
    )

    private fun SpotView.toGql(): GqlSpot = GqlSpot(
        id = id,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        distanceMeters = distanceMeters,
        category = GqlSpotCategory(code = category.code, label = category.code, iconUrl = null),
        copyright = GqlCopyrightType.valueOf(copyright.name),
        attributionText = attributionText,
        mapX = mapX,
        mapY = mapY,
    )

    /** 날짜는 DATE 컬럼이라 LocalDate.toString()이 곧 YYYY-MM-DD다. */
    private fun FestivalView.toGql(): GqlFestival = GqlFestival(
        id = id,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        distanceMeters = distanceMeters,
        category = GqlSpotCategory(code = category.code, label = category.code, iconUrl = null),
        copyright = GqlCopyrightType.valueOf(copyright.name),
        attributionText = attributionText,
        mapX = mapX,
        mapY = mapY,
        eventStartDate = eventStartDate.toString(),
        eventEndDate = eventEndDate.toString(),
    )

    private companion object {
        const val DEFAULT_RADIUS_METERS = 20_000
        const val DEFAULT_PAGE = 1
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_SUGGESTION_LIMIT = 10
    }
}
