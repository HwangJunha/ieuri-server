package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.tourism.application.query.RecommendedRegionQuery
import org.around.ieuri.tourism.application.query.RecommendedRegionView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 지역(시군구) 추천 조회 유스케이스(SPEC-0015 R16~R21·R30·R31).
 *
 * 언어는 인자가 아니라 요청 스코프에서 결정돼 명시 파라미터로 전달된다 — 리졸버가
 * `LocaleContext`에서 읽어 넘기며 application은 서블릿·헤더를 모른다(POL-039).
 */
interface RegionRecommendationUseCase {
    /**
     * 총점 내림차순 추천 목록(R16).
     *
     * [RecommendedRegionQuery.origin]이 null이면 거리 항목 없이 산출한다 — 정상 경로이며
     * 오류가 아니다(R21).
     */
    fun recommendedRegions(query: RecommendedRegionQuery, locale: DomainLocale): RecommendedRegionView
}
