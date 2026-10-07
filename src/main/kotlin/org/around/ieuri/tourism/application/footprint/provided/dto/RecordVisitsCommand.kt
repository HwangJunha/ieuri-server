package org.around.ieuri.tourism.application.footprint.provided.dto

/** 방문 등록 입력(SPEC-0030 R3). 칸 id가 아니라 시군구 코드다. */
data class RecordVisitsCommand(
    val memberId: Long,
    val districtCodes: List<String>,
)
