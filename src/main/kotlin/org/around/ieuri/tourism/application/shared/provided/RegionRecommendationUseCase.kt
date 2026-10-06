package org.around.ieuri.tourism.application.shared.provided

import org.around.ieuri.tourism.application.shared.provided.dto.RecommendedRegionQuery
import org.around.ieuri.tourism.application.shared.provided.dto.RecommendedRegionView
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 지역(시군구) 추천 조회 유스케이스(SPEC-0015). */
interface RegionRecommendationUseCase {
    /**
     * origin이 속한 시도의 다른 시군구를 총점 내림차순으로 추천한다.
     * - origin이 어느 시군구에도 속하지 않거나 시도의 시군구 수가 최소에 못 미치면 오류 없이 빈 목록이다.
     */
    fun recommendedRegions(query: RecommendedRegionQuery, locale: DomainLocale): RecommendedRegionView
}
