package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.common.graphql.generated.types.CellSpot as GqlCellSpot
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.RecordVisitsInput as GqlRecordVisitsInput
import org.around.ieuri.common.graphql.generated.types.RemoveVisitInput as GqlRemoveVisitInput
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.command.RecordVisitsCommand
import org.around.ieuri.tourism.application.command.RemoveVisitCommand
import org.around.ieuri.tourism.application.query.CellSpotView

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
    cellCode = cellCode,
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
)

fun CursorPage<*>.toGqlPageInfo(): GqlPageInfo = GqlPageInfo(
    hasNextPage = pageInfo.hasNextPage,
    hasPreviousPage = pageInfo.hasPreviousPage,
    startCursor = pageInfo.startCursor,
    endCursor = pageInfo.endCursor,
)
