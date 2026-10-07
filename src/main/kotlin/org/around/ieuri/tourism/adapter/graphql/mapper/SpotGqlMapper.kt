package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.common.pagination.OffsetPage
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import org.around.ieuri.common.graphql.generated.types.CopyrightType as GqlCopyrightType
import org.around.ieuri.common.graphql.generated.types.Spot as GqlSpot
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory
import org.around.ieuri.common.graphql.generated.types.SpotImage as GqlSpotImage
import org.around.ieuri.common.graphql.generated.types.SpotInfoItem as GqlSpotInfoItem
import org.around.ieuri.common.graphql.generated.types.SpotIntro as GqlSpotIntro
import org.around.ieuri.common.graphql.generated.types.SpotPage as GqlSpotPage
import org.around.ieuri.common.graphql.generated.types.SpotSearchConnection as GqlSpotSearchConnection

/**
 * 카드 read DTO → GraphQL `Spot`. 카테고리는 코드만 채운다 — label·iconUrl은 요청될 때
 * CategoryDisplayResolver가 붙인다.
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

/** `spots`의 오프셋 페이지 → GraphQL `SpotPage`. */
fun OffsetPage<SpotView>.toGqlSpotPage(): GqlSpotPage = GqlSpotPage(
    items = items.map { it.toGql() },
    page = page,
    hasNext = hasNext,
    totalCount = totalCount,
)

/** 검색의 오프셋 페이지 → GraphQL `SpotSearchConnection`. 검증 실패에도 빈 페이지로 존재한다. */
fun OffsetPage<SpotView>.toGqlSearchConnection(): GqlSpotSearchConnection = GqlSpotSearchConnection(
    items = items.map { it.toGql() },
    page = page,
    hasNext = hasNext,
    totalCount = totalCount,
)

fun SpotImage.toGql(): GqlSpotImage = GqlSpotImage(
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    caption = caption,
    copyright = GqlCopyrightType.valueOf(copyright.name),
    attributionText = attributionText,
)

fun SpotIntro.toGql(): GqlSpotIntro = GqlSpotIntro(
    businessHours = businessHours,
    closedDays = closedDays,
    parking = parking,
    inquiryPhone = inquiryPhone,
    fee = fee,
    petAllowed = petAllowed,
    creditCardAccepted = creditCardAccepted,
)

fun SpotInfoItem.toGql(): GqlSpotInfoItem = GqlSpotInfoItem(name = name, text = text)
