package org.around.ieuri.community.adapter.client.mapper

import org.around.ieuri.community.domain.lookup.SpotSummary
import org.around.ieuri.community.domain.post.TargetCategory
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotSummaryView as TourismSpotSummaryView

/**
 * tourism 응답 → community 장소 요약 변환(POL-033).
 * - 카테고리는 문자열 코드로 건너오며, 모르는 값이면 null이라 카테고리 칩으로 걸러지지 않는다.
 */
fun TourismSpotSummaryView.toSpotSummary(): SpotSummary = SpotSummary(
    id = id,
    title = title,
    thumbnailImageUrl = thumbnailImageUrl,
    category = TargetCategory.fromOrNull(categoryCode),
    longitude = longitude,
    latitude = latitude,
)
