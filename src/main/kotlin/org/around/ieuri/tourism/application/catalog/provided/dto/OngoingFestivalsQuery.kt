package org.around.ieuri.tourism.application.catalog.provided.dto

/** 진행 중 축제 조회 입력. '오늘'은 서버 시계에서 온다. */
data class OngoingFestivalsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val first: Int,
    val after: String?,
)
