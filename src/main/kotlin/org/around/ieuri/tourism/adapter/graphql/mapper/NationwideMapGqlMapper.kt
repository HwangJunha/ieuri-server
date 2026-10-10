package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.footprint.provided.dto.CellImageView
import org.around.ieuri.tourism.application.footprint.provided.dto.CellSpotView
import org.around.ieuri.tourism.application.footprint.provided.dto.MapShareLinkView
import org.around.ieuri.tourism.application.footprint.provided.dto.RecordVisitsCommand
import org.around.ieuri.tourism.application.footprint.provided.dto.RemoveVisitCommand
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationView
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.common.graphql.generated.types.CellImage as GqlCellImage
import org.around.ieuri.common.graphql.generated.types.CellImageKind as GqlCellImageKind
import org.around.ieuri.common.graphql.generated.types.CellSpot as GqlCellSpot
import org.around.ieuri.common.graphql.generated.types.Coordinates as GqlCoordinates
import org.around.ieuri.common.graphql.generated.types.CopyrightType as GqlCopyrightType
import org.around.ieuri.common.graphql.generated.types.MapRelation as GqlMapRelation
import org.around.ieuri.common.graphql.generated.types.MapRelationConnection as GqlMapRelationConnection
import org.around.ieuri.common.graphql.generated.types.MapRelationEdge as GqlMapRelationEdge
import org.around.ieuri.common.graphql.generated.types.MapShareLink as GqlMapShareLink
import org.around.ieuri.common.graphql.generated.types.RecordVisitsInput as GqlRecordVisitsInput
import org.around.ieuri.common.graphql.generated.types.RemoveVisitInput as GqlRemoveVisitInput
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory

/**
 * 전국 지도 GraphQL 경계 변환(POL-033).
 * - 회원 id는 입력이 아니라 인자로 받는다 — 인증 컨텍스트가 정한다.
 */

fun GqlRecordVisitsInput.toCommand(memberId: Long): RecordVisitsCommand = RecordVisitsCommand(
    memberId = memberId,
    districtCodes = districtCodes,
)

fun GqlRemoveVisitInput.toCommand(memberId: Long): RemoveVisitCommand = RemoveVisitCommand(
    memberId = memberId,
    cellId = cellId,
)

/** 칸에 속한 장소 → 응답 타입. `label`은 코드이며 `CategoryDisplayResolver`가 요청 시 표시명으로 덮는다. */
fun CellSpotView.toGql(): GqlCellSpot = GqlCellSpot(
    id = id,
    title = title,
    thumbnailImageUrl = thumbnailImageUrl,
    category = GqlSpotCategory(code = categoryCode, label = categoryCode, iconUrl = null),
    lat = coordinates.latitude,
    lng = coordinates.longitude,
    // GraphQL likeCount는 Int!.
    likeCount = likeCount.toInt(),
)

/** 도메인 좌표(`latitude`/`longitude`) → 응답 좌표(`lat`/`lng`). */
fun Coordinates.toGql(): GqlCoordinates = GqlCoordinates(lat = latitude, lng = longitude)

/** 관계 1건 → 응답 타입. */
fun MapRelationView.toGql(): GqlMapRelation = GqlMapRelation(
    memberId = memberId,
    nickname = nickname,
    overlappingCount = overlappingCount,
    overlappingCellIds = overlappingCellIds,
    theirVisitedCount = theirVisitedCount,
    createdAt = createdAt.toString(),
)

/** 칸 대표 이미지 → 응답 타입. */
fun CellImageView.toGql(): GqlCellImage = GqlCellImage(
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    caption = caption,
    kind = GqlCellImageKind.valueOf(kind.name),
    copyright = GqlCopyrightType.valueOf(copyright.name),
    attributionText = attributionText,
)

fun CursorPage<MapRelationView>.toGqlMapRelationConnection(): GqlMapRelationConnection = GqlMapRelationConnection(
    edges = edges.map { GqlMapRelationEdge(node = it.node.toGql(), cursor = it.cursor) },
    pageInfo = toGqlPageInfo(),
)

fun MapShareLinkView.toGql(): GqlMapShareLink = GqlMapShareLink(shareUrl = shareUrl, createdAt = createdAt.toString())
