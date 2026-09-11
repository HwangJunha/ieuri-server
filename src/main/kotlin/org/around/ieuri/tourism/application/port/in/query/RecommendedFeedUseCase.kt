package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.LocationViewQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * `Location.recommendedSpots`(SPEC-0035 R4~R17). 반경 안 후보를 점수순으로 매기고 DPP로 재배열한
 * 순열의 한 구간을 돌려준다.
 *
 * 커서는 조건 해시·위치·발급 시각·서명을 담은 자립형 토큰이라 캐시 없이도 같은 결과가 나온다(R17).
 * 조건 불일치·서명 실패는 INVALID_CURSOR, 수명 초과는 CURSOR_EXPIRED.
 */
interface RecommendedFeedUseCase {
    fun recommendedSpots(query: LocationViewQuery, locale: DomainLocale, memberId: Long?): CursorPage<SpotView>
}
