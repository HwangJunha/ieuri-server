package org.around.ieuri.tourism.application.region.required.dto

/**
 * 수집된 한 페이지.
 * - [droppedCount]는 필수 필드 누락·숫자 파싱 실패로 어댑터가 버린 행 수다(관측용).
 */
data class RegionMetricSyncPage<T>(
    val records: List<T>,
    val totalCount: Int,
    val droppedCount: Int,
) {
    /** 업스트림에 다음 페이지가 남았는지. 수집 루프의 종료 조건. */
    fun hasMore(pageNo: Int, numOfRows: Int): Boolean = pageNo.toLong() * numOfRows.toLong() < totalCount.toLong()
}
