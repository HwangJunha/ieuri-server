package org.around.ieuri.tourism.application.recommendation.provided.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection

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

/** recommendedSpots 입력(SPEC-0035). 페이지 대신 묶음 크기 [first]만 받는다. */
data class RecommendedSpotsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(`spots`와 같은 규약). */
    val categories: CategorySelection,
    val first: Int,
)
