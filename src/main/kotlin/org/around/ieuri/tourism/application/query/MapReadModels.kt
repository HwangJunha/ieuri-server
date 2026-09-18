package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.CommercialStatus
import org.around.ieuri.tourism.domain.vo.CongestionForecast
import org.around.ieuri.tourism.domain.vo.CongestionSnapshot
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.LocalDateTime

/**
 * mapViewport 쿼리 입력(SPEC-0014 R1). 언어는 인자가 아니라 Accept-Language에서 온다.
 *
 * **커서가 없다** — 지도에는 순서도 "다음 페이지"도 없어 커서 페이지네이션을 쓰지 않는다
 * (POL-017의 명시적 예외, spec §7.1). 대신 줌별 상한과 [MapViewportView.truncated]로
 * "확대하면 더 있다"를 알린다.
 */
data class MapViewportQuery(
    val bounds: MapBounds,
    val zoom: Int,
    /**
     * 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(SPEC-0020 R5).
     * **줌별 상한보다 먼저 적용된다** — 반대 순서면 truncated가 거짓말을 한다(R8).
     */
    val categories: CategorySelection,
)

/**
 * mapRecommendedSpots 쿼리 입력(SPEC-0036). 언어는 인자가 아니라 Accept-Language에서 온다.
 *
 * **커서가 없다**(POL-037) — 「더 보기」는 페이지 넘기기가 아니라 [excludedSpotIds]를 키운
 * 새 묶음 요청이다. 그래서 같은 영역을 다시 물어도 조건이 그대로다(FR-010).
 *
 * [excludedSpotIds]는 **클라이언트가 소유한다** — 「봤다」의 판정이 마커로 실제 그려졌는지이고,
 * 격자로 솎아 그리는 쪽만 그것을 안다. 개수 상한이 없어 세션 내내 커진다.
 *
 * **영역은 도메인 VO가 아니라 원시 좌표로 받는다.** 외부 입력을 도메인 언어로 옮기는 것은
 * 응용 계층의 일이라, 인바운드 어댑터가 [MapBounds]를 조립해 넘기면 그 책임이 전송 계층으로
 * 올라간다 — GraphQL 말고 다른 인바운드가 같은 유스케이스를 부를 때 조립을 각자 반복하게 된다.
 * `recommendedSpots`가 위경도를 그대로 받는 것과 같은 규약이다.
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

/** mapViewport 응답(R1·R3, R12). */
data class MapViewportView(
    val spots: List<MapSpotView>,
    val events: List<CulturalEventView>,
    val truncated: Boolean,
)

/**
 * 지도 마커 1개(R1). 기존 [SpotView]와 달리 **거리를 담지 않는다** — bbox 조회에는 거리를
 * 잴 중심점이 없다.
 *
 * 마커에 필요한 필드만 담는다. 주소·저작권 표기는 마커에 그리지 않으므로, 뷰포트마다
 * 수백 건을 실어 보내지 않는다(상세는 탭 후 spotDetail로 간다).
 */
data class MapSpotView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val category: SpotCategory,
    val lng: Double,
    val lat: Double,
)

/**
 * 문화행사 마커 1건(R12). 업스트림 `EVENT_STTS`에서 온다.
 *
 * [lng]·[lat]는 업스트림 `EVENT_X`·`EVENT_Y`인데 **이름이 직관과 반대다**(X가 경도, Y가
 * 위도). 여기서 바로잡아 담는다 — X/Y를 그대로 넘기면 `(lat, lng)` 생성자에 뒤집어 넣는
 * 사고가 난다.
 *
 * [period]는 `"2026-08-07~2026-08-10"` 형태의 업스트림 원문 그대로다. 시작·종료로 쪼개지
 * 않는다 — 업스트림 형식이 항상 이 형태라는 보장이 없어 파싱 실패가 계약 위반이 된다.
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

/**
 * seoulCongestion 응답(R6~R11).
 *
 * 배열을 그대로 두지 않고 감싼 이유: 업스트림 장애를 최상위 오류가 아니라 **데이터로**
 * 표현하기 위함이다(spec §7.6). [degraded]가 서면 클라이언트는 혼잡도 레이어만 안내로
 * 대체하고 spot·문화행사 마커는 그대로 둔다.
 */
data class SeoulCongestionView(
    val areas: List<CongestionAreaView>,
    val degraded: Boolean,
    val staleSince: LocalDateTime?,
)

/**
 * 구역 1곳의 실시간 혼잡도(R6~R10). **폴리곤 좌표를 담지 않는다** —
 * 경계는 [CongestionAreaBoundaryView]로 분리했고 [id]로 조인한다(R17).
 *
 * [forecast]·[commercial]의 null은 **부재이며 조회 실패가 아니다**(R9·R10).
 */
data class CongestionAreaView(
    val id: String,
    val name: String,
    val congestion: CongestionSnapshot,
    val forecast: List<CongestionForecast>?,
    val commercial: CommercialStatus?,
)

/**
 * 구역 1곳의 정적 경계(R16·R17). 사실상 불변이라 클라이언트가 앱 시작 시 1회 조회해
 * 캐시하는 것을 전제한다.
 *
 * [polygon]은 `[[lng, lat], ...]` 이며 GeoJSON과 같은 순서 규약이다. **닫힌 다각형**이라
 * 첫 점과 끝 점이 같다. 단일 폐곡선만 담는다 — 원본에서 홀이 있는 한 곳(POI096)은
 * 외곽 링만 적재했다.
 */
data class CongestionAreaBoundaryView(
    val id: String,
    val name: String,
    val category: String,
    val polygon: List<List<Double>>,
)
