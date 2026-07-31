package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.locale.LocaleContext
import org.around.ieuri.common.graphql.generated.types.CopyrightType as GqlCopyrightType
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.Spot as GqlSpot
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory
import org.around.ieuri.common.graphql.generated.types.SpotConnection as GqlSpotConnection
import org.around.ieuri.common.graphql.generated.types.SpotEdge as GqlSpotEdge

/**
 * 관광지 조회 GraphQL 쿼리 리졸버(인바운드 어댑터, CQRS 읽기). 언어는 요청 스코프 LocaleContext에서 읽어
 * 유스케이스에 넘긴다(GraphQL 인자 아님, Accept-Language 기반). read DTO를 GraphQL 응답 타입으로 매핑한다.
 * 쿼리 도메인 오류는 TourismQueryExceptionHandler가 최상위 errors[]로 분류한다(payload 컨벤션 없음).
 */
@DgsComponent
class SpotQueryResolver(
    private val spotQueryUseCase: SpotQueryUseCase,
    private val localeContext: LocaleContext,
) {
    @DgsQuery
    fun spots(
        @InputArgument latitude: Double,
        @InputArgument longitude: Double,
        @InputArgument radiusMeters: Int?,
        @InputArgument category: GqlSpotCategory?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlSpotConnection {
        val result = spotQueryUseCase.spots(
            SpotsQuery(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                category = category?.let { SpotCategory.valueOf(it.name) },
                first = first ?: DEFAULT_PAGE_SIZE,
                after = after,
            ),
            locale = localeContext.locale,
        )
        return result.toGql()
    }

    @DgsQuery
    fun availableSpotCategories(): List<GqlSpotCategory> =
        spotQueryUseCase.availableCategories(localeContext.locale)
            .map { GqlSpotCategory.valueOf(it.name) }

    private fun CursorPage<SpotView>.toGql(): GqlSpotConnection = GqlSpotConnection(
        edges = edges.map { GqlSpotEdge(cursor = it.cursor, node = it.node.toGql()) },
        pageInfo = GqlPageInfo(
            hasNextPage = pageInfo.hasNextPage,
            hasPreviousPage = pageInfo.hasPreviousPage,
            startCursor = pageInfo.startCursor,
            endCursor = pageInfo.endCursor,
        ),
        totalCount = totalCount?.toInt(),
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

    private companion object {
        const val DEFAULT_RADIUS_METERS = 20_000
        const val DEFAULT_PAGE_SIZE = 20
    }
}
