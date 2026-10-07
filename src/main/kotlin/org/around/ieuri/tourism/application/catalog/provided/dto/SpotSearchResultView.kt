package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.common.pagination.OffsetPage

/** 검색 응답. 검증 실패도 이 형태로 돌아온다(POL-015) — [userErrors]가 있으면 [items]는 비고 [totalCount]는 0이다. */
data class SpotSearchResultView(
    val userErrors: List<SearchUserError>,
    val results: OffsetPage<SpotView>,
) {
    val success: Boolean get() = userErrors.isEmpty()
}
