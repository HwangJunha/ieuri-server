package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.application.port.`in`.query.MapRecommendationUseCase
import org.around.ieuri.tourism.application.query.MapRecommendedSpotsQuery
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toCategorySelection
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.tourism.infrastructure.locale.domainLocale
import org.around.ieuri.common.graphql.generated.types.MapBoundsInput as GqlMapBoundsInput
import org.around.ieuri.common.graphql.generated.types.MapSpot as GqlMapSpot

/**
 * `Query.mapRecommendedSpots`(SPEC-0036). 인자를 옮기고 응답을 변환할 뿐이다 — 검증·제외·점수·감사는 유스케이스가 한다.
 * - 미인증은 빈 목록이 아니라 UNAUTHENTICATED 오류다.
 */
@DgsComponent
class MapRecommendationResolver(
    private val mapRecommendationUseCase: MapRecommendationUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    @DgsQuery
    fun mapRecommendedSpots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument bbox: GqlMapBoundsInput,
        @InputArgument excludedIds: List<String>?,
        @InputArgument categories: List<String>?,
        @InputArgument first: Int?,
    ): List<GqlMapSpot> = mapRecommendationUseCase.mapRecommendedSpots(
        MapRecommendedSpotsQuery(
            // 좌표를 그대로 넘긴다 — VO 조립과 범위 검증은 응용 계층이 한다.
            minLat = bbox.minLat,
            minLng = bbox.minLng,
            maxLat = bbox.maxLat,
            maxLng = bbox.maxLng,
            // 숫자로 읽히지 않는 id는 어떤 행과도 일치할 수 없으므로 조용히 뺀다.
            excludedSpotIds = excludedIds.orEmpty().mapNotNull(String::toLongOrNull).toSet(),
            categories = toCategorySelection(categories),
            first = first ?: DEFAULT_FIRST,
        ),
        locale = dfe.domainLocale(),
        memberId = currentMemberProvider.requireMemberId(),
    ).map { it.toGql() }

    private companion object {
        /** 계약(`map.graphqls`)의 기본값과 같아야 한다. */
        const val DEFAULT_FIRST = 10
    }
}
