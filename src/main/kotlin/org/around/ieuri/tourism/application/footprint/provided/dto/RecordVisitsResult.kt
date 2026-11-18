package org.around.ieuri.tourism.application.footprint.provided.dto

/**
 * 방문 등록 결과.
 * - [updatedCells]는 이번 요청으로 방문 상태가 된 칸만 담는다.
 * - [unknownDistrictCodes]가 비어 있지 않으면 DISTRICT_NOT_FOUND userError가 된다.
 */
data class RecordVisitsResult(
    val updatedCells: List<MapCellView>,
    val unknownDistrictCodes: List<String>,
)
