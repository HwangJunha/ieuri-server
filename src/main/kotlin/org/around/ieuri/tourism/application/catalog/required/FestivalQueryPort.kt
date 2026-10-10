package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.catalog.provided.dto.FestivalView
import org.around.ieuri.tourism.application.catalog.required.dto.OngoingFestivalSearch

/**
 * 진행 중 축제 조회 아웃바운드 포트(SPEC-0013 R17).
 * - 날짜 필터는 조회 시점에 적용한다 — 배치의 종료 축제 정리에 기대지 않는다.
 */
interface FestivalQueryPort {
    /**
     * 반경 안에서 [asOf]가 행사 기간에 포함된 축제. 거리 오름차순 + id 오름차순.
     * - 최대 `limit`건이다. 다음 페이지 판정과 커서는 포트가 채운다.
     */
    fun findOngoing(query: OngoingFestivalSearch): CursorPage<FestivalView>
}
