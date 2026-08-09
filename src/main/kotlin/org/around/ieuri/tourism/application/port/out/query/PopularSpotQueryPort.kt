package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.PopularSpotView
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
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    val category: SpotCategory?,
    val minLikeCount: Int,
    val limit: Int,
    val after: LikeCountCursor?,
)
