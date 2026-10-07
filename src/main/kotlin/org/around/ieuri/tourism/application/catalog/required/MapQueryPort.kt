package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.tourism.application.catalog.provided.dto.MapSpotView
import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds

/**
 * 지도 뷰포트 조회 아웃바운드 포트(SPEC-0014).
 * - bbox 경계선에 걸친 대상을 포함한다(`ST_Within`이 아니라 `ST_Intersects`).
 */
interface MapQueryPort {
    /** bbox 안의 개별 마커. `limit + 1`건까지 돌려준다 — 초과분은 호출자의 잘림 판정용이다. */
    fun findSpotsWithin(query: MapViewportSearch): List<MapSpotView>

    /**
     * bbox 안의 추천 후보(SPEC-0036). 제외 집합을 DB 단계에서 빼고 중심점 거리를 함께 돌려준다.
     * - 중심점에서 가까운 순이다. 최종 순서는 호출자가 점수로 재배열한다.
     */
    fun findRecommendationCandidatesWithin(query: MapCandidateSearch): List<MapCandidateView>
}

/**
 * bbox 마커 조회 조건. [limit]은 상한 + 1로 넘어온다.
 * - 언어 필터를 빼면 같은 마커가 언어 수만큼 나온다.
 */
data class MapViewportSearch(
    val language: DomainLocale,
    val bounds: MapBounds,
    /** 비어 있으면 전체, 여럿이면 OR. 상한보다 먼저 적용된다. */
    val categories: CategorySelection,
    val limit: Int,
)

/** bbox 추천 후보 조회 조건(SPEC-0036). [limit]은 후보 상한 그대로다(`+1` 아님). */
data class MapCandidateSearch(
    val language: DomainLocale,
    val bounds: MapBounds,
    val categories: CategorySelection,
    /** 제외할 카탈로그 id(반응한 장소 + 이미 표시한 장소). 개수 상한 없음. */
    val excludedSpotIds: Set<Long>,
    val limit: Int,
)

/** 추천 후보 1건. [distanceMeters]는 사용자 위치가 아니라 bbox 중심점까지의 구면 거리다. */
data class MapCandidateView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val category: SpotCategory,
    val lng: Double,
    val lat: Double,
    val distanceMeters: Double,
)
