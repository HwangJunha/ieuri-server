package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.common.graphql.generated.types.CellSpot as GqlCellSpot
import org.around.ieuri.common.graphql.generated.types.Coordinates as GqlCoordinates
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.RecordVisitsInput as GqlRecordVisitsInput
import org.around.ieuri.common.graphql.generated.types.RemoveVisitInput as GqlRemoveVisitInput
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.command.RecordVisitsCommand
import org.around.ieuri.tourism.application.command.RemoveVisitCommand
import org.around.ieuri.common.graphql.generated.types.MapRelation as GqlMapRelation
import org.around.ieuri.common.graphql.generated.types.CellImage as GqlCellImage
import org.around.ieuri.common.graphql.generated.types.CellImageKind as GqlCellImageKind
import org.around.ieuri.common.graphql.generated.types.CopyrightType as GqlCopyrightType
import org.around.ieuri.tourism.application.query.CellImageView
import org.around.ieuri.tourism.application.query.CellSpotView
import org.around.ieuri.tourism.application.query.MapRelationView
import org.around.ieuri.tourism.domain.vo.Coordinates

/**
 * 전국 지도 GraphQL 경계 변환(POL-033).
 *
 * 리졸버 안에 인라인으로 두지 않는 이유는 그러면 리졸버를 세우지 않고는 호출할 수 없어
 * 필드 뒤바뀜을 단위 테스트로 잡을 수 없기 때문이다.
 *
 * **회원 id는 입력이 아니라 인자로 받는다** — 클라이언트가 "누구의 기록인지"를 주장할 수
 * 없어야 한다(인증 컨텍스트가 정한다).
 */

fun GqlRecordVisitsInput.toCommand(memberId: Long): RecordVisitsCommand = RecordVisitsCommand(
    memberId = memberId,
    districtCodes = districtCodes,
)

fun GqlRemoveVisitInput.toCommand(memberId: Long): RemoveVisitCommand = RemoveVisitCommand(
    memberId = memberId,
    cellId = cellId,
)

/**
 * 칸에 속한 장소 → 응답 타입.
 *
 * `label`은 코드를 그대로 넣는다 — `CategoryDisplayResolver`가 요청된 경우에만 실제 표시명으로
 * 덮어쓴다(기존 리졸버들의 관례와 같다).
 */
fun CellSpotView.toGql(): GqlCellSpot = GqlCellSpot(
    id = id,
    title = title,
    thumbnailImageUrl = thumbnailImageUrl,
    category = GqlSpotCategory(code = categoryCode, label = categoryCode, iconUrl = null),
    lat = coordinates.latitude,
    lng = coordinates.longitude,
    // GraphQL likeCount는 Int!. 한 칸의 좋아요 수가 Int 범위를 넘을 일은 없다.
    likeCount = likeCount.toInt(),
)

fun CursorPage<*>.toGqlPageInfo(): GqlPageInfo = GqlPageInfo(
    hasNextPage = pageInfo.hasNextPage,
    hasPreviousPage = pageInfo.hasPreviousPage,
    startCursor = pageInfo.startCursor,
    endCursor = pageInfo.endCursor,
)

/**
 * 도메인 좌표 → 응답 좌표.
 *
 * **필드명이 다르다** — 도메인은 `latitude`/`longitude`, 스키마는 `lat`/`lng`이다. 변환을
 * 빠뜨리고 도메인 값 객체를 그대로 노출하면 DGS가 필드를 찾지 못해 null이 되고,
 * `Coordinates!`가 non-null이라 응답 전체가 무너진다(실측으로 겪었다).
 */
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

/**
 * 칸 대표 이미지 → 응답 타입.
 *
 * 같은 타입의 문자열이 두 쌍 있어(`imageUrl`/`thumbnailUrl`, `caption`/`attributionText`)
 * 뒤바뀌어도 컴파일이 통과한다 — 그래서 필드별 테스트가 이 변환의 안전장치다.
 */
fun CellImageView.toGql(): GqlCellImage = GqlCellImage(
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    caption = caption,
    kind = GqlCellImageKind.valueOf(kind.name),
    copyright = GqlCopyrightType.valueOf(copyright.name),
    attributionText = attributionText,
)
