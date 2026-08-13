package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.port.`in`.query.SpotDiscoveryUseCase
import org.around.ieuri.tourism.application.query.FestivalView
import org.around.ieuri.tourism.application.query.OngoingFestivalsQuery
import org.around.ieuri.tourism.application.query.PopularSpotsQuery
import org.around.ieuri.tourism.application.query.SearchSpotsQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.domain.vo.SpotCategory
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
 * spot 발견 조회 리졸버(SPEC-0013). 언어는 Accept-Language 헤더에서 읽어 유스케이스에 넘긴다
 * (GraphQL 인자가 아니다). read DTO를 GraphQL 응답 타입으로 매핑한다 — application은 DGS 무의존이다.
 *
 * 네 쿼리 모두 비로그인 허용이다. 좌표·반경·카테고리 오류는 TourismQueryExceptionHandler가
 * 최상위 errors[]로 분류하고, **검색어 오류만 payload의 userErrors로** 나간다 — 사용자가 입력창에서
 * 고칠 수 있는 값이기 때문이다.
 *
 * 커넥션 타입 이름이 `Connection`으로 끝나야 한다 — Spring for GraphQL의 ConnectionFieldTypeVisitor가
 * 그렇지 않은 타입은 ConnectionAdapter를 찾다 실패한다(SpotQueryResolver.likedSpots 참조).
 */
@DgsComponent
class SpotDiscoveryResolver(
    private val spotDiscoveryUseCase: SpotDiscoveryUseCase,
) {
    @DgsQuery
    fun searchSpots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument query: String,
        @InputArgument latitude: Double,
        @InputArgument longitude: Double,
        @InputArgument radiusMeters: Int?,
        @InputArgument category: GqlSpotCategory?,
        @InputArgument page: Int?,
        @InputArgument size: Int?,
    ): GqlSpotSearchPayload {
        val result = spotDiscoveryUseCase.searchSpots(
            SearchSpotsQuery(
                query = query,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                category = category?.let { SpotCategory.valueOf(it.name) },
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
            // 검증 실패에도 connection은 존재하며 비어 있다 — 클라이언트가 null 분기 없이 읽는다.
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
    ): List<GqlSpotSuggestion> =
        spotDiscoveryUseCase.spotSearchSuggestions(
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
                category = GqlSpotCategory.valueOf(it.category.name),
            )
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
        @InputArgument category: GqlSpotCategory?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlPopularSpotConnection {
        val page = spotDiscoveryUseCase.popularSpots(
            PopularSpotsQuery(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                category = category?.let { SpotCategory.valueOf(it.name) },
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
                    // likeCount는 순위 근거라 노드가 아니라 엣지의 값이다 — 순위와 무관한
                    // 다른 조회에서는 같은 spot에 대해 의미가 없다.
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
        category = GqlSpotCategory.valueOf(category.name),
        copyright = GqlCopyrightType.valueOf(copyright.name),
        attributionText = attributionText,
        mapX = mapX,
        mapY = mapY,
    )

    /** 날짜는 원본이 DATE 컬럼이라 시각 성분이 없다 — LocalDate.toString()이 곧 YYYY-MM-DD다. */
    private fun FestivalView.toGql(): GqlFestival = GqlFestival(
        id = id,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        distanceMeters = distanceMeters,
        category = GqlSpotCategory.valueOf(category.name),
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
