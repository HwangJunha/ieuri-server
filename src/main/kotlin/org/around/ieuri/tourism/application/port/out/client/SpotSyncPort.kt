package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.application.command.SyncSpotCommand
import org.around.ieuri.tourism.domain.vo.DomainLocale

/** 관광지 전량 동기화 포트(SPEC-0004). 배치가 좌표와 무관하게 언어당 전 페이지를 수집한다. */
interface SpotSyncPort {
    /**
     * [language]의 [pageNo] 페이지(1부터)를 수집한다. 반환된 레코드는 그대로 적재 가능하다.
     * - upstream 장애·타임아웃은 예외로 전파한다.
     */
    fun fetchPage(language: DomainLocale, pageNo: Int, numOfRows: Int): SpotSyncPage
}

/**
 * 수집된 한 페이지. [totalCount]는 upstream이 보고한 필터 이전 총계다.
 * - 건수 필드는 관측용이다. [droppedCount]는 필수 필드 결손, [excludedCount]는 비관광이라 뺀 항목이다.
 */
data class SpotSyncPage(
    val records: List<SyncSpotCommand>,
    val totalCount: Int,
    val droppedCount: Int,
    val categoryFallbackCount: Int,
    /** 비관광 키워드에 걸려 적재하지 않은 항목 수. */
    val excludedCount: Int = 0,
) {
    /** upstream에 다음 페이지가 남았는지. */
    fun hasMore(pageNo: Int, numOfRows: Int): Boolean = pageNo.toLong() * numOfRows.toLong() < totalCount.toLong()
}
