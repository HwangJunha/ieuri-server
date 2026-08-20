package org.around.ieuri.tourism.application.command

import org.around.ieuri.tourism.application.query.MapCellView

/**
 * 방문 등록 입력(SPEC-0030 R3).
 *
 * **칸 코드가 아니라 시군구 코드다** — `resolveDistricts`가 반환한 값을 그대로 넣는다.
 * 칸으로 받으면 광역시 칸 하나가 25개 시군구 전체를 방문 처리해 버린다.
 */
data class RecordVisitsCommand(
    val memberId: Long,
    val districtCodes: List<String>,
)

/**
 * 방문 등록 결과.
 *
 * [updatedCells]는 **이번 요청으로 방문 상태가 된 칸만** 담는다 — 이미 방문한 칸은 빠진다.
 * [unknownDistrictCodes]가 비어 있지 않으면 DISTRICT_NOT_FOUND userError가 된다.
 */
data class RecordVisitsResult(
    val updatedCells: List<MapCellView>,
    val unknownDistrictCodes: List<String>,
)

/** 방문 해제 입력(R4). 칸 코드이며, 그 칸에 속한 시군구 기록이 전부 지워진다. */
data class RemoveVisitCommand(
    val memberId: Long,
    val cellCode: String,
)

/** 방문 해제 결과. 없는 칸이면 [updatedCell]이 null이다. */
data class RemoveVisitResult(
    val updatedCell: MapCellView?,
)
