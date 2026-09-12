package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.LocationViewQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * `Location.recommendedSpots`(SPEC-0035 R4~R16). 반경 안 후보를 점수순으로 매기고 DPP로 재배열한
 * 순열의 한 구간을 돌려준다. 요청마다 다시 계산하며 서버 상태가 없다.
 *
 * 커서는 위치·발급 시각만 담는다. 형식 손상은 INVALID_CURSOR, 수명 초과는 CURSOR_EXPIRED.
 */
interface RecommendedSpotsUseCase {
    fun recommendedSpots(query: LocationViewQuery, locale: DomainLocale, memberId: Long?): CursorPage<SpotView>
}
