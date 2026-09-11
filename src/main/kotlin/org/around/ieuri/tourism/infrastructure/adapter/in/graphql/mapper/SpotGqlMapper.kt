package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.common.graphql.generated.types.CopyrightType as GqlCopyrightType
import org.around.ieuri.common.graphql.generated.types.Spot as GqlSpot
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory

/**
 * 카드 read DTO → GraphQL `Spot`. 목록 계열 리졸버(`spots`·`location.*`·`popularSpots` 등)가 같은
 * 변환을 쓴다. 카테고리는 코드만 채운다 — label·iconUrl은 요청될 때 CategoryDisplayResolver가 붙인다.
 */
fun SpotView.toGql(): GqlSpot = GqlSpot(
    id = id,
    title = title,
    address = address,
    thumbnailImageUrl = thumbnailImageUrl,
    distanceMeters = distanceMeters,
    category = GqlSpotCategory(code = category.code, label = category.code, iconUrl = null),
    copyright = GqlCopyrightType.valueOf(copyright.name),
    attributionText = attributionText,
    mapX = mapX,
    mapY = mapY,
)
