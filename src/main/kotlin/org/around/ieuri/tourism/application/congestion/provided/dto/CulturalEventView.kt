package org.around.ieuri.tourism.application.congestion.provided.dto

/**
 * 문화행사 마커 1건. 업스트림 `EVENT_STTS`에서 온다.
 * - 업스트림 `EVENT_X`가 경도([lng]), `EVENT_Y`가 위도([lat])다.
 * - [period]는 `"2026-08-07~2026-08-10"` 형태의 업스트림 원문 그대로다.
 */
data class CulturalEventView(
    val id: String,
    val title: String,
    val place: String,
    val period: String,
    val lng: Double,
    val lat: Double,
    val url: String?,
)
