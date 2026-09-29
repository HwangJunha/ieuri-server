package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.common.graphql.generated.types.MapSpot as GqlMapSpot
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory

/**
 * 지도 마커 응답 변환(POL-033). `mapViewport`와 `mapRecommendedSpots`가 함께 쓴다.
 * - 라벨·아이콘은 채우지 않는다 — 표시 문구는 `availableSpotCategories`가 담당한다.
 */
fun MapSpotView.toGql(): GqlMapSpot = GqlMapSpot(
    id = id,
    title = title,
    thumbnailImageUrl = thumbnailImageUrl,
    category = GqlSpotCategory(code = category.code, label = category.code, iconUrl = null),
    lng = lng,
    lat = lat,
)
