package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import java.time.LocalDate

/**
 * 끝난 축제(SPEC-0007). 종료일이 기준일보다 이전인 축제이며, 종료일 당일은 아직 진행 중이다([FestivalPeriod.isEndedAsOf]).
 * - 끝난 축제 원본은 정리 단계가 지우고, 수집이 다시 실어 와도 적재하지 않는다 — 지운 행이 되살아나지 않게.
 */
class EndedFestivals(periods: Map<String, FestivalPeriod>, asOf: LocalDate) {
    /** 끝난 축제의 contentId. */
    val contentIds: Set<String> = periods.filterValues { it.isEndedAsOf(asOf) }.keys

    /** [spots]에서 끝난 축제를 뺀다. 순서는 유지한다. */
    fun excludeFrom(spots: List<Spot>): List<Spot> = spots.filterNot { it.contentId in contentIds }
}
