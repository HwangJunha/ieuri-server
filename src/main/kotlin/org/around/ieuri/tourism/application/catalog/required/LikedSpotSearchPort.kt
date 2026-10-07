package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.required.dto.LikedSpotsSearch

/**
 * 좋아요 목록 안에서의 텍스트 검색(SPEC-0033). 관련도가 아니라 좋아요 시각 커서 페이지를 돌려준다.
 * - 질의 검증은 이 포트를 호출하기 전에 끝나 있어야 한다.
 */
interface LikedSpotSearchPort {
    /** 회원의 좋아요 중 [text]에 일치하는 한 페이지. `likedAt DESC, catalogId DESC`이며 검색어는 순서에 영향을 주지 않는다. */
    fun searchLikedSpots(query: LikedSpotsSearch, text: String): CursorPage<LikedSpotView>
}
