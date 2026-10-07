package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotSuggestionView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * spot 전문 검색 포트(SPEC-0013). 질의 문자열을 받아 순위가 매겨진 spot을 돌려준다.
 * - 질의 검증은 이 포트를 호출하기 전에 끝나 있어야 한다.
 */
interface SpotSearchPort {
    /**
     * 반경 안에서 [SpotSearchQuery.text]에 일치하는 spot 한 페이지.
     * 관련도(거리 가중) 내림차순 + id 오름차순으로 [SpotSearchQuery.limit]건까지. 다음 페이지 여부는 [count]로 판정한다.
     */
    fun search(query: SpotSearchQuery): List<SpotView>

    /** [query]와 같은 조건(페이지 제외)에 걸리는 전체 건수. */
    fun count(query: SpotSearchQuery): Int

    /** 반경 안에서 제목이 [prefix]로 시작하는 spot. 제목 오름차순으로 [limit]건까지. */
    fun suggest(prefix: String, language: DomainLocale, center: Coordinates, radiusMeters: Int, limit: Int): List<SpotSuggestionView>
}

/**
 * 좋아요 목록 안에서의 텍스트 검색(SPEC-0033). 관련도가 아니라 좋아요 시각 커서 페이지를 돌려준다.
 * - 질의 검증은 이 포트를 호출하기 전에 끝나 있어야 한다.
 */
interface LikedSpotSearchPort {
    /** 회원의 좋아요 중 [text]에 일치하는 한 페이지. `likedAt DESC, catalogId DESC`이며 검색어는 순서에 영향을 주지 않는다. */
    fun searchLikedSpots(query: LikedSpotsSearch, text: String): CursorPage<LikedSpotView>
}

/** 검색 조건(오프셋 방식). [limit]·[offset]은 호출자가 이미 상한을 적용한 값이다. */
data class SpotSearchQuery(
    val text: String,
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체. 값이 여럿이면 IN으로 거른다(OR). */
    val categories: CategorySelection,
    val limit: Int,
    val offset: Int,
)
