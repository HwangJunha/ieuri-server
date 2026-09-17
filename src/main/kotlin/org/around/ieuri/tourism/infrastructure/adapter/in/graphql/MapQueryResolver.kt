package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.tourism.application.exception.InvalidBboxException
import org.around.ieuri.tourism.application.port.`in`.query.MapQueryUseCase
import org.around.ieuri.tourism.application.query.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.query.CongestionAreaView
import org.around.ieuri.tourism.application.query.CulturalEventView
import org.around.ieuri.tourism.application.query.MapViewportQuery
import org.around.ieuri.tourism.application.query.MapViewportView
import org.around.ieuri.tourism.application.query.SeoulCongestionView
import org.around.ieuri.tourism.domain.vo.CommercialStatus
import org.around.ieuri.tourism.domain.vo.CongestionForecast
import org.around.ieuri.tourism.domain.vo.CongestionSnapshot
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toCategorySelection
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toDomain
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.tourism.infrastructure.locale.domainLocale
import org.around.ieuri.common.graphql.generated.types.CommercialActivityLevel as GqlCommercialActivityLevel
import org.around.ieuri.common.graphql.generated.types.CommercialStatus as GqlCommercialStatus
import org.around.ieuri.common.graphql.generated.types.CongestionArea as GqlCongestionArea
import org.around.ieuri.common.graphql.generated.types.CongestionAreaBoundary as GqlCongestionAreaBoundary
import org.around.ieuri.common.graphql.generated.types.CongestionForecast as GqlCongestionForecast
import org.around.ieuri.common.graphql.generated.types.CongestionLevel as GqlCongestionLevel
import org.around.ieuri.common.graphql.generated.types.CongestionSnapshot as GqlCongestionSnapshot
import org.around.ieuri.common.graphql.generated.types.CulturalEvent as GqlCulturalEvent
import org.around.ieuri.common.graphql.generated.types.MapBoundsInput as GqlMapBoundsInput
import org.around.ieuri.common.graphql.generated.types.MapViewportResult as GqlMapViewportResult
import org.around.ieuri.common.graphql.generated.types.SeoulCongestionResult as GqlSeoulCongestionResult

/**
 * 지도 조회 GraphQL 리졸버(인바운드 어댑터, CQRS 읽기, SPEC-0014). 언어는 요청 스코프
 * Accept-Language 헤더에서 읽어 유스케이스에 넘긴다(GraphQL 인자 아님, Accept-Language 기반).
 *
 * **지연 필드(@DgsData)가 없다.** 세 쿼리 모두 자기 필드를 채우는 데 필요한 I/O가 하나로
 * 묶여 있어, 필드를 나눠도 호출 수가 줄지 않는다:
 *  - `mapViewport`의 spots는 한 번의 공간 질의, events는 그 구역들의 캐시에서 나온다
 *  - `seoulCongestion`의 congestion·forecast·commercial은 **업스트림 1회 응답의 세 블록**이다.
 *    나눠 부르면 같은 구역에 대해 호출이 배가되는데, 일일 쿼터 1000건에서 치명적이다
 *  - `seoulCongestionAreas`는 단일 테이블 조회다
 *
 * 세 쿼리를 **별개 최상위 필드**로 둔 것이 부분 실패 설계의 핵심이다 — 혼잡도의 업스트림
 * 장애가 spot·문화행사 마커에 전파되지 않는다.
 */
@DgsComponent
class MapQueryResolver(
    private val mapQueryUseCase: MapQueryUseCase,
) {

    @DgsQuery
    fun mapViewport(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument bbox: GqlMapBoundsInput,
        @InputArgument zoom: Int,
        @InputArgument categories: List<String>?,
    ): GqlMapViewportResult =
        mapQueryUseCase.mapViewport(
            MapViewportQuery(
                bounds = bbox.toBounds(),
                zoom = zoom,
                categories = toCategorySelection(categories),
            ),
            locale = dfe.domainLocale(),
        ).toGql()

    @DgsQuery
    fun seoulCongestion(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument areaIds: List<String>?,
        @InputArgument bbox: GqlMapBoundsInput?,
    ): GqlSeoulCongestionResult =
        mapQueryUseCase.seoulCongestion(
            areaIds = areaIds,
            bounds = bbox?.toBounds(),
            locale = dfe.domainLocale(),
        ).toGql()

    @DgsQuery
    fun seoulCongestionAreas(dfe: DgsDataFetchingEnvironment): List<GqlCongestionAreaBoundary> =
        mapQueryUseCase.seoulCongestionAreas(dfe.domainLocale()).map { it.toGql() }

    /** 형식 검증은 mapper의 `toDomain`이 하고, 오류 코드 선택만 여기 남는다(R4). */
    private fun GqlMapBoundsInput.toBounds(): MapBounds =
        toDomain() ?: throw InvalidBboxException("지도 영역이 올바르지 않습니다.")

    private fun MapViewportView.toGql() = GqlMapViewportResult(
        spots = spots.map { it.toGql() },
        events = events.map { it.toGql() },
        truncated = truncated,
    )

    private fun CulturalEventView.toGql() = GqlCulturalEvent(
        id = id,
        title = title,
        place = place,
        period = period,
        lng = lng,
        lat = lat,
        url = url,
    )

    private fun SeoulCongestionView.toGql() = GqlSeoulCongestionResult(
        areas = areas.map { it.toGql() },
        degraded = degraded,
        staleSince = staleSince?.toString(),
    )

    private fun CongestionAreaView.toGql() = GqlCongestionArea(
        id = id,
        name = name,
        congestion = congestion.toGql(),
        forecast = forecast?.map { it.toGql() },
        commercial = commercial?.toGql(),
    )

    private fun CongestionSnapshot.toGql() = GqlCongestionSnapshot(
        level = GqlCongestionLevel.valueOf(level.name),
        populationMin = populationMin,
        populationMax = populationMax,
        observedAt = observedAt.toString(),
    )

    private fun CongestionForecast.toGql() = GqlCongestionForecast(
        forecastTime = forecastTime.toString(),
        level = GqlCongestionLevel.valueOf(level.name),
        populationMin = populationMin,
        populationMax = populationMax,
    )

    private fun CommercialStatus.toGql() = GqlCommercialStatus(
        level = GqlCommercialActivityLevel.valueOf(level.name),
        isDefaulted = isDefaulted,
        observedAt = observedAt.toString(),
    )

    private fun CongestionAreaBoundaryView.toGql() = GqlCongestionAreaBoundary(
        id = id,
        name = name,
        category = category,
        polygon = polygon,
    )
}
