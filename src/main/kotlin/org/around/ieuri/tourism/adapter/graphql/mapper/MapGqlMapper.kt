package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.tourism.application.catalog.provided.dto.MapSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.MapViewportView
import org.around.ieuri.tourism.application.catalog.provided.exception.InvalidBboxException
import org.around.ieuri.tourism.application.congestion.provided.dto.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.congestion.provided.dto.CongestionAreaView
import org.around.ieuri.tourism.application.congestion.provided.dto.CulturalEventView
import org.around.ieuri.tourism.application.congestion.provided.dto.SeoulCongestionView
import org.around.ieuri.tourism.domain.congestion.CommercialStatus
import org.around.ieuri.tourism.domain.congestion.CongestionForecast
import org.around.ieuri.tourism.domain.congestion.CongestionSnapshot
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
import org.around.ieuri.common.graphql.generated.types.MapSpot as GqlMapSpot
import org.around.ieuri.common.graphql.generated.types.MapViewportResult as GqlMapViewportResult
import org.around.ieuri.common.graphql.generated.types.SeoulCongestionResult as GqlSeoulCongestionResult
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory

/**
 * 지도 마커 응답 변환(POL-033). `mapViewport`와 `mapRecommendedSpots`가 함께 쓴다.
 * - 라벨·아이콘은 채우지 않는다 — 표시 문구는 `availableSpotCategories`가 담당한다.
 */
fun MapSpotView.toGql(): GqlMapSpot = GqlMapSpot(
    id = id,
    title = title,
    thumbnailImageUrl = thumbnailImageUrl,
    category = GqlSpotCategory(code = category.code, label = category.code, iconUrl = null),
    lng = lng,
    lat = lat,
)

/**
 * 입력 bbox → 도메인 VO. 실패는 `INVALID_BBOX`다(R4).
 * - 어댑터가 VO를 조립하는 기존 형태다 — 새 코드는 따르지 않는다(원시값을 넘기고 응용 계층이 조립한다).
 */
fun GqlMapBoundsInput.toBounds(): MapBounds = MapBounds.ofOrNull(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)
    ?: throw InvalidBboxException("지도 영역이 올바르지 않습니다.")

fun MapViewportView.toGql() = GqlMapViewportResult(
    spots = spots.map { it.toGql() },
    events = events.map { it.toGql() },
    truncated = truncated,
)

fun CulturalEventView.toGql() = GqlCulturalEvent(
    id = id,
    title = title,
    place = place,
    period = period,
    lng = lng,
    lat = lat,
    url = url,
)

fun SeoulCongestionView.toGql() = GqlSeoulCongestionResult(
    areas = areas.map { it.toGql() },
    degraded = degraded,
    staleSince = staleSince?.toString(),
)

fun CongestionAreaView.toGql() = GqlCongestionArea(
    id = id,
    name = name,
    congestion = congestion.toGql(),
    forecast = forecast?.map { it.toGql() },
    commercial = commercial?.toGql(),
)

fun CongestionSnapshot.toGql() = GqlCongestionSnapshot(
    level = GqlCongestionLevel.valueOf(level.name),
    populationMin = populationMin,
    populationMax = populationMax,
    observedAt = observedAt.toString(),
)

fun CongestionForecast.toGql() = GqlCongestionForecast(
    forecastTime = forecastTime.toString(),
    level = GqlCongestionLevel.valueOf(level.name),
    populationMin = populationMin,
    populationMax = populationMax,
)

fun CommercialStatus.toGql() = GqlCommercialStatus(
    level = GqlCommercialActivityLevel.valueOf(level.name),
    isDefaulted = isDefaulted,
    observedAt = observedAt.toString(),
)

fun CongestionAreaBoundaryView.toGql() = GqlCongestionAreaBoundary(
    id = id,
    name = name,
    category = category,
    polygon = polygon,
)
