package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 지도 뷰포트 조회 아웃바운드 포트(SPEC-0014 R1~R3). bbox는 PostGIS `ST_Intersects`로 좁히며
 * **경계선에 걸친 대상을 포함한다**(R1) — `ST_Within`은 경계선상 점을 제외한다.
 */
interface MapQueryPort {
    /**
     * bbox 안의 개별 마커. **`limit + 1`건까지 돌려준다** — 초과분은 호출자의 잘림 판정용이며
     * 응답에 싣지 않는다. `COUNT(*)`를 따로 세지 않는 이유는 정확한 총계가 필요 없고
     * "더 있다"만 알면 되기 때문이다(R3).
     */
    fun findSpotsWithin(query: MapViewportSearch): List<MapSpotView>

    /**
     * bbox 안의 **추천 후보**(SPEC-0036). 점수 계산 대상이라 [findSpotsWithin]보다 많이 가져오며,
     * 반응·이미 표시한 장소를 DB 단계에서 제외하고 중심점 거리를 함께 돌려준다.
     *
     * 정렬은 중심점에서 가까운 순이다 — 상한에 걸려 잘릴 때 가까운 후보가 남게 하기 위한 것이며,
     * 최종 순서는 점수가 정한다(호출자가 재배열한다).
     */
    fun findRecommendationCandidatesWithin(query: MapCandidateSearch): List<MapCandidateView>
}

/**
 * 구역 메타데이터 조회 아웃바운드 포트(R6·R16·R17). 실시간 값은 여기서 오지 않는다 —
 * 정적 메타데이터와 경계만 담당한다.
 */
interface CongestionAreaQueryPort {
    /** 121곳 전체의 경계와 메타데이터(R17). 활성 언어로 이름을 고른 뒤 돌려준다. */
    fun findAllBoundaries(locale: DomainLocale): List<CongestionAreaBoundaryView>

    /**
     * bbox에 걸치는 구역의 코드(R6). 폴리곤이 bbox와 교차하면 포함이다.
     * 뷰포트에 걸치는 구역만 업스트림에 물어보기 위한 것이라 호출량 전략의 핵심이다.
     */
    fun findAreaCodesWithin(bounds: MapBounds): List<String>

    /**
     * 주어진 코드 중 실재하는 구역의 이름. 존재하지 않는 코드는 결과에서 빠지므로
     * 호출자가 `CONGESTION_AREA_NOT_FOUND`를 판정할 수 있다(R6).
     */
    fun findNamesByCodes(areaCodes: List<String>): Map<String, AreaName>
}

/**
 * bbox 마커 조회 조건. [limit]은 상한 + 1로 넘어온다(호출자가 잘림을 판정한다).
 *
 * **언어 필터가 있다** — `spot`은 같은 장소가 언어별로 다른 행이라, 걸지 않으면 같은
 * 마커가 4배로 나온다.
 */
data class MapViewportSearch(
    val language: DomainLocale,
    val bounds: MapBounds,
    /** 카테고리 선택. 비어 있으면 전체. 값이 여럿이면 IN으로 거른다(OR). 상한보다 먼저 적용된다. */
    val categories: CategorySelection,
    val limit: Int,
)

/**
 * bbox 추천 후보 조회 조건(SPEC-0036). [MapViewportSearch]와 갈라 두는 이유는 성격이 달라서다 —
 * 이쪽은 점수 계산 대상이라 제외 집합과 중심점 거리가 필요하고 상한이 훨씬 크다.
 *
 * [limit]은 후보 상한 그대로다 — 잘림을 알릴 필요가 없어 `+1`을 받지 않는다(마커 상한과 달리
 * 소진 판정은 점수 계산 후 결과 수로 한다).
 */
data class MapCandidateSearch(
    val language: DomainLocale,
    val bounds: MapBounds,
    val categories: CategorySelection,
    /** 제외할 카탈로그 id. 반응한 장소와 클라이언트가 이미 표시한 장소가 합쳐져 들어온다. 개수 상한 없음. */
    val excludedSpotIds: Set<Long>,
    val limit: Int,
)

/**
 * 추천 후보 1건. 점수 계산에 필요한 것만 담는다 — 제목은 면세 태그 판정에, 카테고리는 반복 감점에,
 * 거리는 거리 요인에 쓰인다.
 *
 * [distanceMeters]는 **bbox 중심점까지**의 구면 거리다(사용자 위치 아님).
 */
data class MapCandidateView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val category: SpotCategory,
    val lng: Double,
    val lat: Double,
    val distanceMeters: Double,
)
