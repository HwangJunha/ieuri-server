package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.CommercialStatus
import org.around.ieuri.tourism.domain.vo.CongestionForecast
import org.around.ieuri.tourism.domain.vo.CongestionSnapshot
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.LocalDateTime

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

/**
 * mapRecommendedSpots 쿼리 입력(SPEC-0036). 영역은 원시 좌표로 받는다.
 * - 커서가 없다(POL-037). 「더 보기」는 [excludedSpotIds]를 키운 새 묶음 요청이다.
 * - [excludedSpotIds]는 클라이언트가 소유하며 개수 상한이 없다.
 */
data class MapRecommendedSpotsQuery(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
    val excludedSpotIds: Set<Long>,
    val categories: CategorySelection,
    val first: Int,
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

/**
 * 문화행사 마커 1건. 업스트림 `EVENT_STTS`에서 온다.
 * - 업스트림 `EVENT_X`가 경도([lng]), `EVENT_Y`가 위도([lat])다.
 * - [period]는 `"2026-08-07~2026-08-10"` 형태의 업스트림 원문 그대로다.
 */
data class CulturalEventView(
    val id: String,
    val title: String,
    val place: String,
    val period: String,
    val lng: Double,
    val lat: Double,
    val url: String?,
)

/** seoulCongestion 응답. 업스트림 장애는 오류가 아니라 [degraded]로 표현된다. */
data class SeoulCongestionView(
    val areas: List<CongestionAreaView>,
    val degraded: Boolean,
    val staleSince: LocalDateTime?,
)

/**
 * 구역 1곳의 실시간 혼잡도. 경계는 [CongestionAreaBoundaryView]에 있고 [id]로 조인한다.
 * - [forecast]·[commercial]의 null은 부재이며 조회 실패가 아니다.
 */
data class CongestionAreaView(
    val id: String,
    val name: String,
    val congestion: CongestionSnapshot,
    val forecast: List<CongestionForecast>?,
    val commercial: CommercialStatus?,
)

/**
 * 구역 1곳의 정적 경계.
 * - [polygon]은 `[[lng, lat], ...]`(GeoJSON 순서)의 닫힌 단일 폐곡선이며, 첫 점과 끝 점이 같다.
 */
data class CongestionAreaBoundaryView(
    val id: String,
    val name: String,
    val category: String,
    val polygon: List<List<Double>>,
)
