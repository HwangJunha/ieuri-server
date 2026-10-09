package org.around.ieuri.tourism.domain.spot.spec

import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import org.around.ieuri.tourism.domain.shared.Specification
import java.time.LocalDate

/**
 * 끝난 축제(SPEC-0007). 후보는 원본 contentId이며, 종료일이 기준일보다 이전인 축제다 — 종료일 당일은 아직 진행 중이다([FestivalPeriod.isEndedAsOf]).
 * - 끝난 축제 원본은 정리 단계가 지우고, 수집이 다시 실어 와도 적재하지 않는다 — 지운 행이 되살아나지 않게.
 * - 기간을 모르는 contentId(축제가 아닌 원본)는 끝나지 않은 것이다.
 */
class EndedFestival(periods: Map<String, FestivalPeriod>, asOf: LocalDate) : Specification<String> {

    private val endedContentIds: Set<String> = periods.filterValues { it.isEndedAsOf(asOf) }.keys

    override fun isSatisfiedBy(candidate: String): Boolean = candidate in endedContentIds
}
