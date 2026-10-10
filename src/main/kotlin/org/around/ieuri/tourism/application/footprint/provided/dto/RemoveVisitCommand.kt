package org.around.ieuri.tourism.application.footprint.provided.dto

/** 방문 해제 입력(R4). 칸 코드이며, 그 칸에 속한 시군구 기록이 전부 지워진다. */
data class RemoveVisitCommand(
    val memberId: Long,
    val cellId: Long,
)
