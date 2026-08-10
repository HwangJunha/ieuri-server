package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.tourism.application.exception.InvalidBboxException
import org.around.ieuri.tourism.application.port.`in`.query.MapQueryUseCase
import org.around.ieuri.tourism.application.query.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.query.CongestionAreaView
import org.around.ieuri.tourism.application.query.CulturalEventView
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.application.query.MapViewportQuery
import org.around.ieuri.tourism.application.query.MapViewportView
import org.around.ieuri.tourism.application.query.SeoulCongestionView
import org.around.ieuri.tourism.domain.vo.CommercialStatus
import org.around.ieuri.tourism.domain.vo.CongestionForecast
import org.around.ieuri.tourism.domain.vo.CongestionSnapshot
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.locale.LocaleContext
import org.around.ieuri.common.graphql.generated.types.CommercialActivityLevel as GqlCommercialActivityLevel
import org.around.ieuri.common.graphql.generated.types.CommercialStatus as GqlCommercialStatus
import org.around.ieuri.common.graphql.generated.types.CongestionArea as GqlCongestionArea
import org.around.ieuri.common.graphql.generated.types.CongestionAreaBoundary as GqlCongestionAreaBoundary
import org.around.ieuri.common.graphql.generated.types.CongestionForecast as GqlCongestionForecast
import org.around.ieuri.common.graphql.generated.types.CongestionLevel as GqlCongestionLevel
import org.around.ieuri.common.graphql.generated.types.CongestionSnapshot as GqlCongestionSnapshot
import org.around.ieuri.common.graphql.generated.types.CulturalEvent as GqlCulturalEvent
import org.around.ieuri.common.graphql.generated.types.MapBoundsInput as GqlMapBoundsInput
import org.around.ieuri.common.graphql.generated.types.MapSpot as GqlMapSpot
import org.around.ieuri.common.graphql.generated.types.MapViewportResult as GqlMapViewportResult
import org.around.ieuri.common.graphql.generated.types.SeoulCongestionResult as GqlSeoulCongestionResult
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory

/**
 * 지도 조회 GraphQL 리졸버(인바운드 어댑터, CQRS 읽기, SPEC-0014). 언어는 요청 스코프
 * `LocaleContext`에서 읽어 유스케이스에 넘긴다(GraphQL 인자 아님, Accept-Language 기반).
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
    private val localeContext: LocaleContext,
) {

    @DgsQuery
    fun mapViewport(
        @InputArgument bbox: GqlMapBoundsInput,
        @InputArgument zoom: Int,
        @InputArgument category: GqlSpotCategory?,
    ): GqlMapViewportResult =
        mapQueryUseCase.mapViewport(
            MapViewportQuery(
                bounds = bbox.toDomain(),
                zoom = zoom,
                category = category?.let { SpotCategory.valueOf(it.name) },
            ),
            locale = localeContext.locale,
        ).toGql()

    @DgsQuery
    fun seoulCongestion(
        @InputArgument areaIds: List<String>?,
        @InputArgument bbox: GqlMapBoundsInput?,
    ): GqlSeoulCongestionResult =
        mapQueryUseCase.seoulCongestion(
            areaIds = areaIds,
            bounds = bbox?.toDomain(),
            locale = localeContext.locale,
        ).toGql()

    @DgsQuery
    fun seoulCongestionAreas(): List<GqlCongestionAreaBoundary> =
        mapQueryUseCase.seoulCongestionAreas(localeContext.locale).map { it.toGql() }

    /**
     * 입력 bbox → 도메인 VO. 좌표 범위·뒤집힘 검증이 여기서 일어나며, 실패는
     * `INVALID_BBOX`다(R4).
     *
     * 형식 검증을 인바운드 어댑터에서 하는 것이 프로젝트 규약이다 — 도메인은 GraphQL
     * 오류 코드를 모르므로 VO는 null을 돌려주고 코드 선택은 여기서 한다.
     */
    private fun GqlMapBoundsInput.toDomain(): MapBounds =
        MapBounds.ofOrNull(
            minLat = minLat,
            minLng = minLng,
            maxLat = maxLat,
            maxLng = maxLng,
        ) ?: throw InvalidBboxException("지도 영역이 올바르지 않습니다.")

    private fun MapViewportView.toGql() = GqlMapViewportResult(
        spots = spots.map { it.toGql() },
        events = events.map { it.toGql() },
        truncated = truncated,
    )

    private fun MapSpotView.toGql() = GqlMapSpot(
        id = id,
        title = title,
        thumbnailImageUrl = thumbnailImageUrl,
        category = GqlSpotCategory.valueOf(category.name),
        lng = lng,
        lat = lat,
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
