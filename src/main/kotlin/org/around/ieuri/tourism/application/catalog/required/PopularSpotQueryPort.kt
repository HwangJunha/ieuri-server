package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.tourism.application.catalog.provided.dto.PopularSpotView
import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.catalog.LikeCountCursor
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 인기 spot 조회 포트(SPEC-0013). LIKE를 기간 제한 없이 spot 단위로 집계한다. */
interface PopularSpotQueryPort {
    /** 반경 안에서 좋아요 수 내림차순 + id 오름차순. 다음 페이지 판정용으로 `limit + 1`건까지 돌려준다. */
    fun findPopular(query: PopularSpotSearch): List<PopularSpotView>
}

/** 인기 spot 조회 조건. [minLikeCount]는 집계 후 적용하는 하한이다. */
data class PopularSpotSearch(
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체. 값이 여럿이면 IN으로 거른다(OR). */
    val categories: CategorySelection,
    val minLikeCount: Int,
    val limit: Int,
    val after: LikeCountCursor?,
)
