package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.tourism.application.congestion.provided.dto.CulturalEventView
import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.MapBounds

/**
 * mapViewport 쿼리 입력(SPEC-0014). 언어는 Accept-Language에서 온다.
 * - 커서가 없다(POL-017 예외). 줌별 상한에 걸리면 [MapViewportView.truncated]가 선다.
 */
data class MapViewportQuery(
    val bounds: MapBounds,
    val zoom: Int,
    /** 카테고리 선택. 비어 있으면 전체, 여럿이면 OR이다. 줌별 상한보다 먼저 적용된다. */
    val categories: CategorySelection,
)

/** mapViewport 응답. */
data class MapViewportView(
    val spots: List<MapSpotView>,
    val events: List<CulturalEventView>,
    val truncated: Boolean,
)

/** 지도 마커 1개. 마커에 필요한 필드만 담으며 거리는 없다. */
data class MapSpotView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val category: SpotCategory,
    val lng: Double,
    val lat: Double,
)
