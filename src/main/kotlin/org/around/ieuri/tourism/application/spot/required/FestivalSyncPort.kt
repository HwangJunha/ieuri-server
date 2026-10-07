package org.around.ieuri.tourism.application.spot.required

import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotFestival
import java.time.LocalDate

/** 축제 전용 정보 수집 포트(SPEC-0007). */
interface FestivalSyncPort {
    /**
     * [language]의 [pageNo] 페이지(1부터)를 수집한다.
     * - [eventStartDate]는 "그 날짜에 시작하는 행사"가 아니라 "그 날짜 기준 유효한 행사"를 뜻한다.
     * - upstream 장애·타임아웃은 예외로 전파한다.
     */
    fun fetchPage(
        language: DomainLocale,
        eventStartDate: LocalDate,
        pageNo: Int,
        numOfRows: Int,
    ): FestivalSyncPage
}

/** 수집된 한 페이지. [droppedCount]는 날짜 해석 실패 등으로 드랍한 관측용 건수다. */
data class FestivalSyncPage(
    val records: List<SpotFestival>,
    val totalCount: Int,
    val droppedCount: Int,
) {
    /** upstream에 다음 페이지가 남았는지. */
    fun hasMore(pageNo: Int, numOfRows: Int): Boolean = pageNo.toLong() * numOfRows.toLong() < totalCount.toLong()
}
