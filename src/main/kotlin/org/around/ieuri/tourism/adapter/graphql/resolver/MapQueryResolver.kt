package org.around.ieuri.tourism.adapter.graphql.resolver

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.tourism.adapter.graphql.mapper.toCategorySelection
import org.around.ieuri.tourism.adapter.graphql.mapper.toGql
import org.around.ieuri.tourism.adapter.graphql.support.domainLocale
import org.around.ieuri.tourism.application.shared.provided.MapQueryUseCase
import org.around.ieuri.tourism.application.shared.provided.dto.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.shared.provided.dto.CongestionAreaView
import org.around.ieuri.tourism.application.shared.provided.dto.CulturalEventView
import org.around.ieuri.tourism.application.shared.provided.dto.MapViewportQuery
import org.around.ieuri.tourism.application.shared.provided.dto.MapViewportView
import org.around.ieuri.tourism.application.shared.provided.dto.SeoulCongestionView
import org.around.ieuri.tourism.application.shared.provided.exception.InvalidBboxException
import org.around.ieuri.tourism.domain.shared.CommercialStatus
import org.around.ieuri.tourism.domain.shared.CongestionForecast
import org.around.ieuri.tourism.domain.shared.CongestionSnapshot
import org.around.ieuri.tourism.domain.shared.MapBounds
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
 * 지도 조회 GraphQL 리졸버(CQRS 읽기, SPEC-0014). 언어는 Accept-Language 헤더에서 읽는다.
 * - 세 쿼리는 각자 I/O가 하나로 묶여 지연 필드가 없다.
 * - 세 쿼리를 별개 루트로 두어 혼잡도의 업스트림 장애가 spot·문화행사 마커에 번지지 않는다.
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
    ): GqlMapViewportResult = mapQueryUseCase.mapViewport(
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
    ): GqlSeoulCongestionResult = mapQueryUseCase.seoulCongestion(
        areaIds = areaIds,
        bounds = bbox?.toBounds(),
        locale = dfe.domainLocale(),
    ).toGql()

    @DgsQuery
    fun seoulCongestionAreas(dfe: DgsDataFetchingEnvironment): List<GqlCongestionAreaBoundary> = mapQueryUseCase.seoulCongestionAreas(dfe.domainLocale()).map { it.toGql() }

    /**
     * 입력 bbox → 도메인 VO. 실패는 `INVALID_BBOX`다(R4).
     * - 어댑터가 VO를 조립하는 기존 형태다 — 새 코드는 따르지 않는다(원시값을 넘기고 응용 계층이 조립한다).
     */
    private fun GqlMapBoundsInput.toBounds(): MapBounds = MapBounds.ofOrNull(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)
        ?: throw InvalidBboxException("지도 영역이 올바르지 않습니다.")

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
