package org.around.ieuri.tourism.application.spot.required.dto

import org.around.ieuri.tourism.domain.spot.Spot

/**
 * 수집된 한 페이지. [totalCount]는 upstream이 보고한 필터 이전 총계다.
 * - 건수 필드는 관측용이다. [droppedCount]는 필수 필드 결손, [excludedCount]는 비관광이라 뺀 항목이다.
 */
data class SpotSyncPage(
    val records: List<Spot>,
    val totalCount: Int,
    val droppedCount: Int,
    val categoryFallbackCount: Int,
    /** 비관광 키워드에 걸려 적재하지 않은 항목 수. */
    val excludedCount: Int = 0,
) {
    /** upstream에 다음 페이지가 남았는지. */
    fun hasMore(pageNo: Int, numOfRows: Int): Boolean = pageNo.toLong() * numOfRows.toLong() < totalCount.toLong()
}
