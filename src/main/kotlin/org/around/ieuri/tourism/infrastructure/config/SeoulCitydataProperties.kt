package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 서울시 실시간 도시데이터 접근 설정(SPEC-0014 §9·spec §12). 인증키는 **환경변수로만
 * 주입한다** — 저장소에 커밋하지 않는다.
 *
 * **[apiKey]가 비어 있어도 기동에 실패하지 않는다.** 혼잡도 조회만 degraded로 응답하고
 * 지도의 나머지는 정상 동작한다(R11) — 로컬 개발에서 키 없이 지도를 띄울 수 있어야 한다.
 *
 * @param minRefetchSeconds 같은 구역 재호출 최소 간격(초). 데이터가 약 30분 지연되어
 *   도착하므로 5분 경계 TTL은 같은 데이터를 반복 호출한다 — `[TBD]`이며 실측 후 크게 잡는다.
 * @param maxAreaIds 한 번에 조회할 구역 수 상한(R6). 벌크 호출이 없어 구역 수가 곧
 *   호출 수라, 클램프가 아니라 거절이다.
 */
@ConfigurationProperties(prefix = "ieuri.seoul-citydata")
data class SeoulCitydataProperties(
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
 * 지도 뷰포트 조회 설정(SPEC-0014 §9). 여기 수치는 전부 spec §8의 `[TBD]`이며 실측으로
 * 확정한다 — 기본값은 카카오맵 줌 규약과 서울 면적을 근거로 잡은 출발점이다.
 *
 * 카카오맵 줌은 1~14 범위이며 **숫자가 클수록 확대**다.
 *
 * @param clusterZoomThreshold 이 줌 미만이면 클러스터링(R2).
 * @param maxAreaSquareDegrees 뷰포트 면적 상한(도 곱, R5).
 * @param maxSpotMarkers 개별 마커 상한(R3).
 * @param maxClusters 클러스터 상한(R3).
 * @param maxEventMarkers 문화행사 마커 상한.
 * @param clusterGridSizeAtMinZoom 최저 줌의 그리드 셀 크기(도). 줌 1당 절반이 된다.
 */
@ConfigurationProperties(prefix = "ieuri.map-viewport")
data class MapViewportProperties(
    val minZoom: Int = 1,
    val maxZoom: Int = 14,
    val clusterZoomThreshold: Int = 10,
    val maxAreaSquareDegrees: Double = 4.0,
    val maxSpotMarkers: Int = 500,
    val maxClusters: Int = 100,
    val maxEventMarkers: Int = 200,
    val clusterGridSizeAtMinZoom: Double = 1.0,
)
