package org.around.ieuri.tourism.application.footprint.provided.dto

/** 방문 등록 입력(SPEC-0030 R3). 칸 id가 아니라 시군구 코드다. 회원 id는 담지 않는다. */
data class RecordVisitsCommand(
    val districtCodes: List<String>,
)
