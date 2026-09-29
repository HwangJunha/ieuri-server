package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.tourism.application.query.RecommendedSpotsQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 내 주변 추천(SPEC-0035). 페이지가 아니라 묶음이며 서버 상태가 없다. 인증된 회원만 부른다.
 * - `first`보다 적게 오면 반경 안 후보가 소진된 것이다.
 */
interface RecommendedSpotsUseCase {
    fun recommendedSpots(query: RecommendedSpotsQuery, locale: DomainLocale, memberId: Long): List<SpotView>
}
