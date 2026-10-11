package org.around.ieuri.tourism.application.footprint.provided.dto

/**
 * 방문 등록 결과.
 * - [addedDistrictCodes]는 이번 요청으로 방문이 된 시군구만 담는다. 이미 방문이던 코드는 빠진다.
 * - [unknownDistrictCodes]가 비어 있지 않으면 DISTRICT_NOT_FOUND userError가 된다.
 */
data class RecordVisitsResult(
    val addedDistrictCodes: List<String>,
    val unknownDistrictCodes: List<String>,
)
