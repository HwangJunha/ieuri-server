package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.tourism.application.catalog.provided.dto.FestivalView
import org.around.ieuri.tourism.domain.catalog.DistanceCursor
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.DomainLocale
import java.time.LocalDate

/**
 * 진행 중 축제 조회 아웃바운드 포트(SPEC-0013 R17).
 * - 날짜 필터는 조회 시점에 적용한다 — 배치의 종료 축제 정리에 기대지 않는다.
 */
interface FestivalQueryPort {
    /**
     * 반경 안에서 [asOf]가 행사 기간에 포함된 축제. 거리 오름차순 + id 오름차순.
     * - `limit + 1`건까지 돌려준다 — 초과분은 다음 페이지 판정용이다.
     */
    fun findOngoing(query: OngoingFestivalSearch): List<FestivalView>
}

/**
 * 진행 중 축제 조회 조건. [asOf]는 서비스가 시계에서 읽어 넘긴 '오늘'이다.
 * - 경계를 포함한다: `event_start_date <= asOf AND event_end_date >= asOf`.
 */
data class OngoingFestivalSearch(
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    val asOf: LocalDate,
    val limit: Int,
    val after: DistanceCursor?,
)
