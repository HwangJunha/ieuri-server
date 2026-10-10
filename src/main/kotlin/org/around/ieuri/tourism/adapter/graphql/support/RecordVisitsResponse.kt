package org.around.ieuri.tourism.adapter.graphql.support

import org.around.ieuri.common.graphql.generated.types.UserError
import org.around.ieuri.tourism.application.footprint.provided.dto.MapCellView
/**
 * 방문 뮤테이션의 응답 타입. 코드젠 `RecordVisitsPayload` 대신 읽기 DTO([MapCellView])를 담아 지연 필드를 살린다.
 * - 이름을 `...Response`로 둔다 — 코드젠 동명 클래스와 혼동하지 않게 한다(DGS는 프로퍼티 이름으로 필드를 찾는다).
 */
data class RecordVisitsResponse(
    val success: Boolean,
    val userErrors: List<UserError>,
    val updatedCells: List<MapCellView>,
)
