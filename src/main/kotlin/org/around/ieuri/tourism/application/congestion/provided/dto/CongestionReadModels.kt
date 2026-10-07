package org.around.ieuri.tourism.application.congestion.provided.dto

import org.around.ieuri.tourism.domain.congestion.CommercialStatus
import org.around.ieuri.tourism.domain.congestion.CongestionForecast
import org.around.ieuri.tourism.domain.congestion.CongestionSnapshot
import java.time.LocalDateTime

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
