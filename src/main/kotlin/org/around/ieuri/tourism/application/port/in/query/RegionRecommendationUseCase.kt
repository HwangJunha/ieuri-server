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
     * 총점 내림차순 추천 목록(R16). 대상은 [RecommendedRegionQuery.origin]이 속한 시도이며
     * origin의 시군구 자신은 빠진다(R21).
     *
     * **빈 목록이 정상인 경우가 있다** — origin이 어느 시군구에도 속하지 않거나(바다·해외),
     * 그 시도의 후보가 최소 시군구 수에 못 미칠 때(세종·제주)다. 어느 쪽도 오류가 아니다.
     */
    fun recommendedRegions(query: RecommendedRegionQuery, locale: DomainLocale): RecommendedRegionView
}
