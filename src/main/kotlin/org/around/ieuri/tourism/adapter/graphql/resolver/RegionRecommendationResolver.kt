package org.around.ieuri.tourism.adapter.graphql.resolver

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.common.graphql.tokenPresentation
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.adapter.graphql.support.domainLocale
import org.around.ieuri.tourism.application.shared.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.shared.provided.RegionRecommendationUseCase
import org.around.ieuri.tourism.application.shared.provided.dto.RecommendedRegionItemView
import org.around.ieuri.tourism.application.shared.provided.dto.RecommendedRegionQuery
import org.around.ieuri.tourism.application.shared.provided.dto.RecommendedRegionView
import org.around.ieuri.tourism.application.shared.provided.dto.RegionScoreView
import org.around.ieuri.tourism.application.shared.provided.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.shared.provided.exception.InvalidLimitException
import org.around.ieuri.tourism.application.shared.required.TourismEventPublisherPort
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.common.graphql.generated.types.Coordinates as GqlCoordinates
import org.around.ieuri.common.graphql.generated.types.CoordinatesInput as GqlCoordinatesInput
import org.around.ieuri.common.graphql.generated.types.RecommendedRegion as GqlRecommendedRegion
import org.around.ieuri.common.graphql.generated.types.RecommendedRegionResult as GqlRecommendedRegionResult
import org.around.ieuri.common.graphql.generated.types.RegionScore as GqlRegionScore
import org.around.ieuri.common.graphql.generated.types.RegionScoreFactor as GqlRegionScoreFactor

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
    private val eventPublisher: TourismEventPublisherPort,
) {

    /** 추천 시군구 목록. 입력 검증(형식·범위)은 이 어댑터가 한다. */
    @DgsQuery
    fun recommendedRegions(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument origin: GqlCoordinatesInput,
        @InputArgument limit: Int?,
    ): GqlRecommendedRegionResult {
        // 좌표 조회 사실을 남긴다(SPEC-0032 R19). origin은 넘기지 않는다.
        // 만료 토큰은 비로그인으로 흘리지 않고 오류다. 비로그인은 발행하지 않는다.
        currentMemberProvider.optionalMemberId(dfe.tokenPresentation())
            ?.let { eventPublisher.publish(LocationAccessed(it, "recommendedRegions")) }
        return regionRecommendationUseCase.recommendedRegions(
            RecommendedRegionQuery(
                origin = origin.toDomain(),
                limit = resolveLimit(limit),
            ),
            locale = dfe.domainLocale(),
        ).toGql()
    }

    /** 개수 검증(R29 · POL-018). 0 이하는 거절, 상한 초과는 클램프다. */
    private fun resolveLimit(requested: Int?): Int {
        if (requested != null && requested <= 0) {
            throw InvalidLimitException("요청 개수는 1 이상이어야 합니다.")
        }
        return policy.resolveLimit(requested)
    }

    /** 좌표 범위 위반은 `INVALID_COORDINATES`다(R29). 예외 메시지에 좌표를 담지 않는다(POL-013). */
    private fun GqlCoordinatesInput.toDomain(): Coordinates = runCatching { Coordinates(latitude = lat, longitude = lng) }
        .getOrElse { throw InvalidCoordinatesException("좌표가 올바르지 않습니다.") }

    private fun RecommendedRegionView.toGql() = GqlRecommendedRegionResult(
        regions = regions.map { it.toGql() },
    )

    private fun RecommendedRegionItemView.toGql() = GqlRecommendedRegion(
        code = code,
        name = name,
        provinceName = provinceName,
        scores = scores.map { it.toGql() },
        scoreFactorCount = scoreFactorCount,
        center = center.toGql(),
    )

    private fun RegionScoreView.toGql() = GqlRegionScore(
        factor = GqlRegionScoreFactor.valueOf(score.factor.name),
        value = score.value,
        rank = rank,
    )

    private fun Coordinates.toGql() = GqlCoordinates(lat = latitude, lng = longitude)
}
