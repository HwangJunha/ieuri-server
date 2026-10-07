package org.around.ieuri.tourism.adapter.graphql.resolver

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.tokenPresentation
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.adapter.graphql.mapper.toDomain
import org.around.ieuri.tourism.adapter.graphql.mapper.toGql
import org.around.ieuri.tourism.adapter.graphql.support.domainLocale
import org.around.ieuri.tourism.application.region.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.region.provided.RegionRecommendationUseCase
import org.around.ieuri.tourism.application.region.provided.dto.RecommendedRegionQuery
import org.around.ieuri.tourism.application.region.provided.exception.InvalidLimitException
import org.around.ieuri.common.graphql.generated.types.CoordinatesInput as GqlCoordinatesInput
import org.around.ieuri.common.graphql.generated.types.RecommendedRegionResult as GqlRecommendedRegionResult

/**
 * 지역 추천 GraphQL 리졸버(CQRS 읽기, SPEC-0015). 언어는 Accept-Language 헤더에서 온다.
 * - 모든 필드가 한 번의 조회에서 나와 지연 필드가 없다.
 * - origin은 로깅하지 않는다(POL-013 · POL-045).
 */
@DgsComponent
class RegionRecommendationResolver(
    private val regionRecommendationUseCase: RegionRecommendationUseCase,
    private val policy: RegionRecommendationPolicy,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    /** 추천 시군구 목록. 입력 검증(형식·범위)은 이 어댑터가 한다. */
    @DgsQuery
    fun recommendedRegions(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument origin: GqlCoordinatesInput,
        @InputArgument limit: Int?,
    ): GqlRecommendedRegionResult {
        // 만료 토큰은 비로그인으로 흘리지 않고 오류다 — 조회 기록 없이 지나가지 않게 한다.
        val memberId = currentMemberProvider.optionalMemberId(dfe.tokenPresentation())
        return regionRecommendationUseCase.recommendedRegions(
            RecommendedRegionQuery(
                origin = origin.toDomain(),
                limit = resolveLimit(limit),
            ),
            locale = dfe.domainLocale(),
            memberId = memberId,
        ).toGql()
    }

    /** 개수 검증(R29 · POL-018). 0 이하는 거절, 상한 초과는 클램프다. */
    private fun resolveLimit(requested: Int?): Int {
        if (requested != null && requested <= 0) {
            throw InvalidLimitException("요청 개수는 1 이상이어야 합니다.")
        }
        return policy.resolveLimit(requested)
    }
}
