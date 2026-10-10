package org.around.ieuri.tourism.application.catalog.provided.dto

/** 시군구 귀속 결과. [unassigned]는 바다·경계 밖 좌표라 0이 되지 않는다 — 갑자기 커지면 경계·좌표 적재를 의심한다. */
data class DistrictAssignmentResult(
    val assigned: Int,
    val unassigned: Int,
)
