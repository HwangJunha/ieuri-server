package org.around.ieuri.tourism.application.shared.provided.dto

/** 카탈로그 한 행의 요약. upstream 호출 없이 만들어지며, 다른 도메인이 장소 확인·표시에 쓴다. */
data class SpotSummaryView(
    /** `spot_catalog.id`. 다른 도메인이 장소를 저장할 때 쓰는 id다 — 언어별 `spot.id`가 아니다. */
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    /** 카테고리 코드 문자열. */
    val categoryCode: String,
    val longitude: Double,
    val latitude: Double,
)
