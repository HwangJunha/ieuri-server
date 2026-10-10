package org.around.ieuri.tourism.application.footprint.provided.dto

/** 공유 지도 조회 결과. */
data class SharedMapView(
    val ownerNickname: String,
    val visitedCellIds: List<Long>,
    /** 비인증 조회면 null이다 — 빈 목록(겹치는 칸 없음)과 구분된다. */
    val overlappingCellIds: List<Long>?,
    val visitedCount: Int,
)
