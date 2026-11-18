package org.around.ieuri.tourism.application.region.provided.dto

import org.around.ieuri.tourism.domain.shared.Coordinates

/** recommendedRegions 쿼리 입력(SPEC-0015). [limit]은 어댑터가 이미 클램프한 값이다. */
data class RecommendedRegionQuery(
    val origin: Coordinates,
    val limit: Int,
)
