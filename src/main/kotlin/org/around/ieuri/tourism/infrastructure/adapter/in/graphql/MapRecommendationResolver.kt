package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.application.exception.InvalidBboxException
import org.around.ieuri.tourism.application.port.`in`.query.MapRecommendationUseCase
import org.around.ieuri.tourism.application.query.MapRecommendedSpotsQuery
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toCategorySelection
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toDomain
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.tourism.infrastructure.locale.domainLocale
import org.around.ieuri.common.graphql.generated.types.MapBoundsInput as GqlMapBoundsInput
import org.around.ieuri.common.graphql.generated.types.MapSpot as GqlMapSpot

/**
 * `Query.mapRecommendedSpots`(SPEC-0036). 인자를 옮기고 응답을 변환할 뿐이다 — 면적 검증·제외·
 * 점수·감사는 유스케이스가 한다. 미인증은 빈 목록이 아니라 UNAUTHENTICATED 오류다.
 *
 * `MapQueryResolver`와 갈라 두는 이유는 인증 경계가 다르기 때문이다 — 뷰포트 마커·문화행사·
 * 혼잡도는 비로그인에서도 동작해야 하고 이 필드는 회원 단위다.
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
    ): List<GqlMapSpot> =
        mapRecommendationUseCase.mapRecommendedSpots(
            MapRecommendedSpotsQuery(
                bounds = bbox.toDomain() ?: throw InvalidBboxException("지도 영역이 올바르지 않습니다."),
                // 숫자로 읽히지 않는 id는 존재할 수 없는 장소이므로 제외 목록에서 조용히 뺀다 —
                // 거절하면 클라이언트가 들고 있는 목록 하나 때문에 지도 전체가 실패한다.
                excludedSpotIds = excludedIds.orEmpty().mapNotNull(String::toLongOrNull).toSet(),
                categories = toCategorySelection(categories),
                first = first ?: DEFAULT_FIRST,
            ),
            locale = dfe.domainLocale(),
            memberId = currentMemberProvider.requireMemberId(),
        ).map { it.toGql() }

    private companion object {
        /** 계약의 기본값과 같아야 한다(`map.graphqls`). 화면에 몇 개를 그릴지는 클라이언트가 정한다. */
        const val DEFAULT_FIRST = 10
    }
}
