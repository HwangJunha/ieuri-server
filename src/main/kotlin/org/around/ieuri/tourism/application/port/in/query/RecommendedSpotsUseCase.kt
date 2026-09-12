package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.tourism.application.query.RecommendedSpotsQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 내 주변 추천(SPEC-0035). **페이지가 아니라 묶음이다** — 이미 반응한 장소를 뺀 뒤 점수·다양성으로
 * 앞 `first`건을 준다. 다시 부르면 그 시점의 새 추천이 오며 서버 상태가 없다.
 * `first`보다 적게 오면 반경 안 후보가 소진된 것이다. 인증된 회원만 부른다.
 */
interface RecommendedSpotsUseCase {
    fun recommendedSpots(query: RecommendedSpotsQuery, locale: DomainLocale, memberId: Long): List<SpotView>
}
