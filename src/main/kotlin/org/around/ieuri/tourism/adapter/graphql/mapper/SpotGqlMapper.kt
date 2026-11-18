package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.OffsetPage
import org.around.ieuri.tourism.application.catalog.provided.dto.FestivalView
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.PopularSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotSuggestionView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.application.category.provided.dto.CategoryDisplay
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import org.around.ieuri.common.graphql.generated.types.CopyrightType as GqlCopyrightType
import org.around.ieuri.common.graphql.generated.types.Festival as GqlFestival
import org.around.ieuri.common.graphql.generated.types.FestivalConnection as GqlFestivalConnection
import org.around.ieuri.common.graphql.generated.types.FestivalEdge as GqlFestivalEdge
import org.around.ieuri.common.graphql.generated.types.LikedSpot as GqlLikedSpot
import org.around.ieuri.common.graphql.generated.types.LikedSpotConnection as GqlLikedSpotConnection
import org.around.ieuri.common.graphql.generated.types.LikedSpotEdge as GqlLikedSpotEdge
import org.around.ieuri.common.graphql.generated.types.PopularSpotConnection as GqlPopularSpotConnection
import org.around.ieuri.common.graphql.generated.types.PopularSpotEdge as GqlPopularSpotEdge
import org.around.ieuri.common.graphql.generated.types.Spot as GqlSpot
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory
import org.around.ieuri.common.graphql.generated.types.SpotImage as GqlSpotImage
import org.around.ieuri.common.graphql.generated.types.SpotInfoItem as GqlSpotInfoItem
import org.around.ieuri.common.graphql.generated.types.SpotIntro as GqlSpotIntro
import org.around.ieuri.common.graphql.generated.types.SpotPage as GqlSpotPage
import org.around.ieuri.common.graphql.generated.types.SpotSearchConnection as GqlSpotSearchConnection
import org.around.ieuri.common.graphql.generated.types.SpotSuggestion as GqlSpotSuggestion

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
    category = category.toGqlCodeOnly(),
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

/**
 * 코드만 채운 카테고리. label·iconUrl은 요청될 때 CategoryDisplayResolver가 언어에 맞춰 채운다.
 * - 그 전까지 label은 코드와 같다.
 */
fun SpotCategory.toGqlCodeOnly(): GqlSpotCategory = GqlSpotCategory(code = code, label = defaultLabel, iconUrl = null)

/** 앱 구성의 카테고리 1건. 표시 정보가 이미 해석돼 있다. */
fun CategoryDisplay.toGql(): GqlSpotCategory = GqlSpotCategory(code = category.code, label = label, iconUrl = iconUrl)

fun SpotSuggestionView.toGql(): GqlSpotSuggestion = GqlSpotSuggestion(id = id, title = title, category = category.toGqlCodeOnly())

/** 날짜는 DATE 컬럼이라 LocalDate.toString()이 곧 YYYY-MM-DD다. */
fun FestivalView.toGql(): GqlFestival = GqlFestival(
    id = spot.id,
    title = spot.title,
    address = spot.address,
    thumbnailImageUrl = spot.thumbnailImageUrl,
    distanceMeters = spot.distanceMeters,
    category = spot.category.toGqlCodeOnly(),
    copyright = GqlCopyrightType.valueOf(spot.copyright.name),
    attributionText = spot.attributionText,
    mapX = spot.mapX,
    mapY = spot.mapY,
    eventStartDate = eventStartDate.toString(),
    eventEndDate = eventEndDate.toString(),
)

fun LikedSpotView.toGql(): GqlLikedSpot = GqlLikedSpot(
    id = id,
    title = title,
    thumbnailImageUrl = thumbnailImageUrl,
    address = address,
    category = category.toGqlCodeOnly(),
    copyright = GqlCopyrightType.valueOf(copyright.name),
    attributionText = attributionText,
)

/** 좋아요 커넥션. likedAt은 노드가 아니라 엣지(회원-spot 관계)의 값이다. 총 개수는 내보내지 않는다. */
fun CursorPage<LikedSpotView>.toGqlLikedSpotConnection(): GqlLikedSpotConnection = GqlLikedSpotConnection(
    edges = edges.map { GqlLikedSpotEdge(cursor = it.cursor, node = it.node.toGql(), likedAt = it.node.likedAt.toString()) },
    pageInfo = toGqlPageInfo(),
)

fun CursorPage<FestivalView>.toGqlFestivalConnection(): GqlFestivalConnection = GqlFestivalConnection(
    edges = edges.map { GqlFestivalEdge(cursor = it.cursor, node = it.node.toGql()) },
    pageInfo = toGqlPageInfo(),
)

/** 인기 커넥션. likeCount는 순위 근거라 엣지의 값이다. */
fun CursorPage<PopularSpotView>.toGqlPopularSpotConnection(): GqlPopularSpotConnection = GqlPopularSpotConnection(
    edges = edges.map { GqlPopularSpotEdge(cursor = it.cursor, node = it.node.spot.toGql(), likeCount = it.node.likeCount.toInt()) },
    pageInfo = toGqlPageInfo(),
)
