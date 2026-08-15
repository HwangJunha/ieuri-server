package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.PopularSpotView
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.LikeCountCursor
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 인기 spot 조회 아웃바운드 포트(SPEC-0013 R18). `spot_reaction`의 LIKE를 spot 단위로 집계한다.
 * 기간 제한이 없어 전체 기간을 센다.
 */
interface PopularSpotQueryPort {
    /**
     * 반경 안에서 좋아요 수 내림차순 + id 오름차순.
     * **`limit + 1`건까지 돌려준다** — 초과분은 호출자의 다음 페이지 판정용이며 응답에 싣지 않는다.
     */
    fun findPopular(query: PopularSpotSearch): List<PopularSpotView>
}

/**
 * 인기 spot 조회 조건. [minLikeCount]는 집계 **후** 적용하는 하한이며(AC-18.2), 정책값이라
 * 클라이언트가 고를 수 없다. 이 기준을 넘는 spot이 없으면 빈 결과가 정상이다(AC-18.4).
 */
data class PopularSpotSearch(
    /**
     * 원문 보유 필터. **null이면 전체**, true면 그 언어 원문이 있는 것만, false면 번역본만.
     *
     * 클라이언트가 원문을 먼저 소진한 뒤 false로 넘어오는 흐름을 위한 값이다 — 서버가
     * 정렬로 끼워넣지 않는다. 정렬로 하면 거리·인기순을 요청했는데 다른 축이 앞서는
     * 숨은 규칙이 되고, 커서에도 그 축을 실어야 해서 형식이 깨진다.
     */
    val hasOrigin: Boolean?,
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체. 값이 여럿이면 IN으로 거른다(OR). */
    val categories: CategorySelection,
    val minLikeCount: Int,
    val limit: Int,
    val after: LikeCountCursor?,
)
