package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.catalog.provided.dto.PopularSpotView
import org.around.ieuri.tourism.application.catalog.required.dto.PopularSpotSearch

/** 인기 spot 조회 포트(SPEC-0013). LIKE를 기간 제한 없이 spot 단위로 집계한다. */
interface PopularSpotQueryPort {
    /** 반경 안에서 좋아요 수 내림차순 + id 오름차순. 최대 `limit`건이며 다음 페이지 판정과 커서는 포트가 채운다. */
    fun findPopular(query: PopularSpotSearch): CursorPage<PopularSpotView>
}
