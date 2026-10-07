package org.around.ieuri.tourism.adapter.config

import org.springframework.boot.context.properties.ConfigurationProperties
/**
 * 지도 뷰포트 조회 설정(SPEC-0014 §9). 카카오맵 줌은 1~14이며 숫자가 클수록 확대다.
 *
 * @param maxAreaSquareDegrees 뷰포트 면적 상한(도 곱, R5).
 * @param maxSpotMarkers 개별 마커 상한(R3).
 * @param maxEventMarkers 문화행사 마커 상한.
 */
@ConfigurationProperties(prefix = "ieuri.map-viewport")
data class MapViewportProperties(
    val minZoom: Int = 1,
    val maxZoom: Int = 14,
    val maxAreaSquareDegrees: Double = 4.0,
    val maxSpotMarkers: Int = 500,
    val maxEventMarkers: Int = 200,
    val recommendationCandidateCap: Int = 2000,
    val maxRecommendationSize: Int = 100,
)
