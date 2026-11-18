package org.around.ieuri.tourism.application.footprint.provided.dto

/** 방문 해제 결과. 없는 칸이면 [updatedCell]이 null이다. */
data class RemoveVisitResult(
    val updatedCell: MapCellView?,
)
