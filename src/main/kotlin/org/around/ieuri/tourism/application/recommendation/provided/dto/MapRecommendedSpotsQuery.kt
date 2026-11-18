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
