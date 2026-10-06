package org.around.ieuri.community.adapter.client.mapper

import org.around.ieuri.community.application.shared.dto.PostSpotView
import org.around.ieuri.community.domain.shared.TargetCategory
import org.around.ieuri.tourism.application.shared.provided.dto.SpotSummaryView

/**
 * tourism 응답 → 커뮤니티 read DTO 변환(POL-033).
 * - 카테고리는 문자열 코드로 건너오며, 모르는 값이면 null이라 카테고리 칩으로 걸러지지 않는다.
 */
fun SpotSummaryView.toPostSpotView(): PostSpotView = PostSpotView(
    id = id,
    title = title,
    thumbnailImageUrl = thumbnailImageUrl,
    category = TargetCategory.fromOrNull(categoryCode),
    longitude = longitude,
    latitude = latitude,
)
