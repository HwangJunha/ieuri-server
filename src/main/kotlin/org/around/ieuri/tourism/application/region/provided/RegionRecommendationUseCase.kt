package org.around.ieuri.tourism.application.region.provided

import org.around.ieuri.tourism.application.region.provided.dto.RecommendedRegionQuery
import org.around.ieuri.tourism.application.region.provided.dto.RecommendedRegionView
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 지역(시군구) 추천 조회 유스케이스(SPEC-0015). */
interface RegionRecommendationUseCase {
    /**
     * origin이 속한 시도의 다른 시군구를 총점 내림차순으로 추천한다.
     * - origin이 어느 시군구에도 속하지 않거나 시도의 시군구 수가 최소에 못 미치면 오류 없이 빈 목록이다.
     * - [memberId]가 있으면 좌표 조회 사실을 남긴다(SPEC-0032 R19). null은 비로그인이다.
     */
    fun recommendedRegions(query: RecommendedRegionQuery, locale: DomainLocale, memberId: Long?): RecommendedRegionView
}
