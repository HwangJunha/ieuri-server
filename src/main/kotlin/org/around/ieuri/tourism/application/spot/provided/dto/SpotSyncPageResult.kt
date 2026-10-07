package org.around.ieuri.tourism.application.spot.provided.dto

/**
 * 한 페이지의 결과. [contentIds]는 이 페이지가 본 원본 전부 — 적재에서 뺀 종료 축제도 들어 있다(정리의 생존 집합).
 * - 건수 필드는 관측용이다. [dropped]는 필수 필드 결손, [excluded]는 비관광이라 뺀 항목이다(SPEC-0018).
 */
data class SpotSyncPageResult(
    val written: Int,
    val contentIds: List<String>,
    val hasMore: Boolean,
    val dropped: Int,
    val categoryFallback: Int = 0,
    val excluded: Int = 0,
)
