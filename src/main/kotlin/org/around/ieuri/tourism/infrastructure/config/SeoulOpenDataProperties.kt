package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 서울시 실시간 도시데이터 접근 설정(SPEC-0014 §9). 인증키는 환경변수로만 주입한다.
 * - [apiKey]가 비어 있어도 기동한다 — 혼잡도 조회만 degraded다(R11).
 *
 * @param minRefetchSeconds 같은 구역 재호출 최소 간격(초).
 * @param maxAreaIds 한 번에 조회할 구역 수 상한(R6). 초과는 클램프가 아니라 거절이다.
 */
@ConfigurationProperties(prefix = "ieuri.seoul-open-data")
data class SeoulOpenDataProperties(
    val apiKey: String = "",
    val baseUrl: String = "http://openapi.seoul.go.kr:8088",
    val minRefetchSeconds: Long = 300,
    val maxAreaIds: Int = 20,
    val connectTimeoutMillis: Long = 5_000,
    val readTimeoutMillis: Long = 10_000,
) {
    val minRefetchInterval: Duration get() = Duration.ofSeconds(minRefetchSeconds)
}

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
