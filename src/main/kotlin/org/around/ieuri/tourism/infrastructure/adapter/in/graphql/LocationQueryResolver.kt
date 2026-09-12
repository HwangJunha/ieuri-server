package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.tokenPresentation
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.application.port.`in`.query.LocationQueryUseCase
import org.around.ieuri.tourism.application.port.`in`.query.RecommendedSpotsUseCase
import org.around.ieuri.tourism.application.query.LocationView
import org.around.ieuri.tourism.application.query.LocationViewQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toCategorySelection
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toGqlPageInfo
import org.around.ieuri.tourism.infrastructure.locale.domainLocale
import org.around.ieuri.common.graphql.generated.types.SpotConnection as GqlSpotConnection
import org.around.ieuri.common.graphql.generated.types.SpotEdge as GqlSpotEdge

/**
 * `Query.location`과 그 두 보기(SPEC-0035). 루트는 좌표 검증만 하고 DB를 보지 않는다 — 보기 필드가
 * 선택될 때만 각자 조회한다. 부모가 돌려준 read DTO([LocationView])를 `getSource`로 받는다
 * (생성 타입은 non-null 보기 필드를 생성자 인자로 요구해 비워 둘 수 없다).
 *
 * 두 형제 필드는 가상 스레드에서 동시에 풀릴 수 있다 — 공유 가변 상태를 두지 않는다.
 * 커넥션 타입 이름은 `Connection`으로 끝나야 한다(SpotQueryResolver.likedSpots 참조).
 */
@DgsComponent
class LocationQueryResolver(
    private val locationQueryUseCase: LocationQueryUseCase,
    private val recommendedSpotsUseCase: RecommendedSpotsUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    @DgsQuery
    fun location(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument latitude: Double,
        @InputArgument longitude: Double,
    ): LocationView = locationQueryUseCase.location(latitude, longitude, dfe.viewerMemberId())

    @DgsData(parentType = "Location", field = "spots")
    fun spots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument radiusMeters: Int?,
        @InputArgument categories: List<String>?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlSpotConnection =
        locationQueryUseCase.spots(
            dfe.viewQuery(radiusMeters, categories, first, after),
            locale = dfe.domainLocale(),
            memberId = dfe.viewerMemberId(),
        ).toGql()

    @DgsData(parentType = "Location", field = "recommendedSpots")
    fun recommendedSpots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument radiusMeters: Int?,
        @InputArgument categories: List<String>?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlSpotConnection =
        recommendedSpotsUseCase.recommendedSpots(
            dfe.viewQuery(radiusMeters, categories, first, after),
            locale = dfe.domainLocale(),
            memberId = dfe.viewerMemberId(),
        ).toGql()

    /** 두 보기가 같은 인자를 같은 규칙으로 읽는다. 좌표는 부모가 검증해 둔 값이다. */
    private fun DgsDataFetchingEnvironment.viewQuery(
        radiusMeters: Int?,
        categories: List<String>?,
        first: Int?,
        after: String?,
    ): LocationViewQuery {
        val location = getSource<LocationView>()
            ?: error("Location 필드 리졸버는 부모가 반환한 LocationView를 소스로 받아야 한다.")
        return LocationViewQuery(
            latitude = location.latitude,
            longitude = location.longitude,
            radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
            categories = toCategorySelection(categories),
            first = first ?: DEFAULT_PAGE_SIZE,
            after = after,
        )
    }

    /** 비로그인은 null이지만 만료 토큰은 오류다(SPEC-0032 R1) — 반응 제외가 조용히 풀리지 않게. */
    private fun DgsDataFetchingEnvironment.viewerMemberId(): Long? =
        currentMemberProvider.optionalMemberId(tokenPresentation())

    private fun CursorPage<SpotView>.toGql() = GqlSpotConnection(
        edges = edges.map { GqlSpotEdge(node = it.node.toGql(), cursor = it.cursor) },
        pageInfo = toGqlPageInfo(),
    )

    private companion object {
        const val DEFAULT_RADIUS_METERS = 20_000
        const val DEFAULT_PAGE_SIZE = 20
    }
}
