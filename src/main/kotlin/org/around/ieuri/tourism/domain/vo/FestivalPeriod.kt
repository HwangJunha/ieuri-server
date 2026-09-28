package org.around.ieuri.tourism.domain.vo

import java.time.LocalDate

/**
 * 축제 행사 기간(SPEC-0007 R-4·R-8). 기간 불변식과 종료 판정 규칙을 둔다.
 * - 종료일이 시작일보다 앞설 수 없다.
 */
data class FestivalPeriod(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        // AC-15.
        require(!end.isBefore(start)) { "행사 종료일은 시작일보다 앞설 수 없습니다." }
    }

    /**
     * [date] 기준으로 이미 끝난 행사인가(R-8).
     * - 종료일 당일은 아직 끝나지 않았다(AC-27).
     */
    fun isEndedAsOf(date: LocalDate): Boolean = end.isBefore(date)
}
