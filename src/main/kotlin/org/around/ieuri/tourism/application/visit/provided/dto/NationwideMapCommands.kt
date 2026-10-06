package org.around.ieuri.tourism.application.visit.provided.dto

import org.around.ieuri.tourism.application.shared.provided.dto.MapCellView

/** 방문 등록 입력(SPEC-0030 R3). 칸 id가 아니라 시군구 코드다. */
data class RecordVisitsCommand(
    val memberId: Long,
    val districtCodes: List<String>,
)

/**
 * 방문 등록 결과.
 * - [updatedCells]는 이번 요청으로 방문 상태가 된 칸만 담는다.
 * - [unknownDistrictCodes]가 비어 있지 않으면 DISTRICT_NOT_FOUND userError가 된다.
 */
data class RecordVisitsResult(
    val updatedCells: List<MapCellView>,
    val unknownDistrictCodes: List<String>,
)

/** 방문 해제 입력(R4). 칸 코드이며, 그 칸에 속한 시군구 기록이 전부 지워진다. */
data class RemoveVisitCommand(
    val memberId: Long,
    val cellId: Long,
)

/** 방문 해제 결과. 없는 칸이면 [updatedCell]이 null이다. */
data class RemoveVisitResult(
    val updatedCell: MapCellView?,
)
