package org.around.ieuri.tourism.adapter.graphql.resolver

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.adapter.graphql.mapper.toCategorySelection
import org.around.ieuri.tourism.adapter.graphql.mapper.toGql
import org.around.ieuri.tourism.adapter.graphql.support.domainLocale
import org.around.ieuri.tourism.application.recommendation.provided.RecommendedSpotsUseCase
import org.around.ieuri.tourism.application.recommendation.provided.dto.RecommendedSpotsQuery
import org.around.ieuri.common.graphql.generated.types.Spot as GqlSpot

/**
 * `Query.recommendedSpots`(SPEC-0035). 인자를 옮기고 응답을 변환할 뿐이다 — 검증·제외·점수·감사는
 * 유스케이스가 한다. 미인증은 빈 목록이 아니라 UNAUTHENTICATED 오류다.
 */
@DgsComponent
class RecommendedSpotsResolver(
    private val recommendedSpotsUseCase: RecommendedSpotsUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    @DgsQuery
    fun recommendedSpots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument latitude: Double,
        @InputArgument longitude: Double,
        @InputArgument radiusMeters: Int?,
        @InputArgument categories: List<String>?,
        @InputArgument first: Int?,
    ): List<GqlSpot> = recommendedSpotsUseCase.recommendedSpots(
        RecommendedSpotsQuery(
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
            categories = toCategorySelection(categories),
            first = first ?: DEFAULT_FIRST,
        ),
        locale = dfe.domainLocale(),
        memberId = currentMemberProvider.requireMemberId(),
    ).map { it.toGql() }

    private companion object {
        const val DEFAULT_RADIUS_METERS = 20_000
        const val DEFAULT_FIRST = 20
    }
}
