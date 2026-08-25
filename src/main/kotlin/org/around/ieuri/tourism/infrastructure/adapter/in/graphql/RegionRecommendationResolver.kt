package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import org.around.ieuri.common.graphql.rejectStaleToken
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.application.port.out.client.LocationAuditPort
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
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
import org.around.ieuri.tourism.infrastructure.locale.domainLocale
import org.around.ieuri.common.graphql.generated.types.Coordinates as GqlCoordinates
import org.around.ieuri.common.graphql.generated.types.CoordinatesInput as GqlCoordinatesInput
import org.around.ieuri.common.graphql.generated.types.RecommendedRegion as GqlRecommendedRegion
import org.around.ieuri.common.graphql.generated.types.RecommendedRegionResult as GqlRecommendedRegionResult
import org.around.ieuri.common.graphql.generated.types.RegionScore as GqlRegionScore
import org.around.ieuri.common.graphql.generated.types.RegionScoreFactor as GqlRegionScoreFactor

/**
 * 지역 추천 GraphQL 리졸버(인바운드 어댑터, CQRS 읽기, SPEC-0015). 언어는 GraphQL 인자가
 * 아니라 Accept-Language 헤더에서 온다.
 *
 * **지연 필드(@DgsData)를 두지 않는다.** 응답의 모든 필드가 한 번의 지표 조회와 그 위의 단일
 * 점수 산출 패스에서 나와, 필드를 나눠도 줄어드는 I/O가 없다.
 *
 * **origin은 로깅하지 않는다**(POL-013 · POL-045) — 사용자 본인의 위치다.
 */
@DgsComponent
class RegionRecommendationResolver(
    private val regionRecommendationUseCase: RegionRecommendationUseCase,
    private val policy: RegionRecommendationPolicy,
    private val currentMemberProvider: CurrentMemberProvider,
    private val locationAuditPort: LocationAuditPort,
) {

    /**
     * 추천 시군구 목록. 입력 검증(형식·범위)은 인바운드 어댑터의 몫이다 — 도메인은 GraphQL
     * 오류 코드를 모른다.
     */
    @DgsQuery
    fun recommendedRegions(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument origin: GqlCoordinatesInput,
        @InputArgument limit: Int?,
    ): GqlRecommendedRegionResult {
        // 좌표를 받는 연산이므로 조회 사실을 남긴다(SPEC-0032 R19). **origin은 넘기지 않는다.**
        //
        // **거절된 토큰을 비로그인으로 흘리지 않는다** — 만료를 비로그인으로 다루면 로그인한
        // 회원의 위치 조회가 기록 없이 지나가고, 그 구멍이 액세스 TTL마다 열린다.
        dfe.rejectStaleToken()
        locationAuditPort.recordAccess(
            currentMemberProvider.currentMemberId(),
            "recommendedRegions",
        )
        return regionRecommendationUseCase.recommendedRegions(
            RecommendedRegionQuery(
                origin = origin.toDomain(),
                limit = resolveLimit(limit),
            ),
            locale = dfe.domainLocale(),
        ).toGql()
    }

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
