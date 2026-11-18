package org.around.ieuri.tourism.adapter.graphql.support

import org.around.ieuri.common.graphql.generated.types.UserError
import org.around.ieuri.tourism.application.footprint.provided.dto.MapCellView
/** 방문 해제 응답. 위와 같은 이유로 생성 타입이 아니다. */
data class RemoveVisitResponse(
    val success: Boolean,
    val userErrors: List<UserError>,
    val updatedCell: MapCellView?,
)
