package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.InvalidLimitException
import org.around.ieuri.tourism.application.policy.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.port.`in`.query.RegionRecommendationUseCase
import org.around.ieuri.tourism.application.query.RecommendedRegionItemView
import org.around.ieuri.tourism.application.query.RecommendedRegionQuery
import org.around.ieuri.tourism.application.query.RecommendedRegionView
import org.around.ieuri.tourism.application.query.RegionScoreView
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.infrastructure.locale.LocaleContext
import org.around.ieuri.common.graphql.generated.types.Coordinates as GqlCoordinates
import org.around.ieuri.common.graphql.generated.types.CoordinatesInput as GqlCoordinatesInput
import org.around.ieuri.common.graphql.generated.types.RecommendedRegion as GqlRecommendedRegion
import org.around.ieuri.common.graphql.generated.types.RecommendedRegionResult as GqlRecommendedRegionResult
import org.around.ieuri.common.graphql.generated.types.RegionScore as GqlRegionScore
import org.around.ieuri.common.graphql.generated.types.RegionScoreFactor as GqlRegionScoreFactor

/**
 * 지역 추천 GraphQL 리졸버(인바운드 어댑터, CQRS 읽기, SPEC-0015). 언어는 요청 스코프
 * `LocaleContext`에서 읽어 유스케이스에 넘긴다(GraphQL 인자 아님, Accept-Language 기반).
 *
 * **지연 필드(@DgsData)를 두지 않는 것이 맞다.** 응답의 모든 필드(이름·총점·항목별 점수·대표
 * 좌표)가 **한 번의 지표 조회와 그 위의 단일 점수 산출 패스**에서 나온다. 필드를 나눠도
 * 줄어드는 I/O가 없고, 정규화 척도가 전체 집합에 의존해 한 지역만 따로 계산할 수도 없다.
 *
 * **origin은 로깅하지 않는다**(POL-013 · POL-045) — 사용자 본인의 위치다. 저장도 하지 않으며
 * 거리 점수 계산에만 쓰인다. `Coordinates.toString()`이 이미 마스킹되어 있으나, 이 리졸버에
 * 로깅을 추가할 때도 이 인자는 제외한다.
 */
@DgsComponent
class RegionRecommendationResolver(
    private val regionRecommendationUseCase: RegionRecommendationUseCase,
    private val policy: RegionRecommendationPolicy,
    private val localeContext: LocaleContext,
) {

    /**
     * 추천 시군구 목록. 입력 검증(형식·범위)은 인바운드 어댑터의 몫이다 — 도메인은 GraphQL
     * 오류 코드를 모른다.
     */
    @DgsQuery
    fun recommendedRegions(
        @InputArgument origin: GqlCoordinatesInput,
        @InputArgument limit: Int?,
    ): GqlRecommendedRegionResult =
        regionRecommendationUseCase.recommendedRegions(
            RecommendedRegionQuery(
                origin = origin.toDomain(),
                limit = resolveLimit(limit),
            ),
            locale = localeContext.locale,
        ).toGql()

    /**
     * 개수 검증(R29 · POL-018). **0 이하는 거절, 상한 초과는 클램프**다 — 두 방향의 처리가
     * 다르다. 0건 요청은 의도로 성립하지 않아 조용히 기본값으로 바꾸지 않는다.
     */
    private fun resolveLimit(requested: Int?): Int {
        if (requested != null && requested <= 0) {
            throw InvalidLimitException("요청 개수는 1 이상이어야 합니다.")
        }
        return policy.resolveLimit(requested)
    }

    /** 좌표 범위 위반은 `INVALID_COORDINATES`다(R29). 예외 메시지에 좌표를 담지 않는다(POL-013). */
    private fun GqlCoordinatesInput.toDomain(): Coordinates =
        runCatching { Coordinates(latitude = lat, longitude = lng) }
            .getOrElse { throw InvalidCoordinatesException("좌표가 올바르지 않습니다.") }

    private fun RecommendedRegionView.toGql() = GqlRecommendedRegionResult(
        regions = regions.map { it.toGql() },
    )

    private fun RecommendedRegionItemView.toGql() = GqlRecommendedRegion(
        code = code,
        name = name,
        provinceName = provinceName,
        totalScore = totalScore,
        scores = scores.map { it.toGql() },
        scoreFactorCount = scoreFactorCount,
        center = center.toGql(),
    )

    /** 항목별 기준일은 여기서 문자열이 된다 — DISTANCE처럼 기준일이 없는 항목은 null이다. */
    private fun RegionScoreView.toGql() = GqlRegionScore(
        factor = GqlRegionScoreFactor.valueOf(score.factor.name),
        value = score.value,
        basisAt = basisAt?.toString(),
    )

    private fun Coordinates.toGql() = GqlCoordinates(lat = latitude, lng = longitude)
}
