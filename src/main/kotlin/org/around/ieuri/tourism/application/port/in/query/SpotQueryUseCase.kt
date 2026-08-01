package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.tourism.application.query.SpotDetailView
import org.around.ieuri.tourism.application.query.SpotImageView
import org.around.ieuri.tourism.application.query.SpotInfoItemView
import org.around.ieuri.tourism.application.query.SpotIntroView
import org.around.ieuri.tourism.application.query.SpotPageView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광지 조회 인바운드 포트(CQRS 읽기). 언어는 요청 스코프에서 결정돼 명시 파라미터로 전달된다
 * (리졸버가 LocaleContext에서 읽어 넘긴다 — application은 서블릿/헤더를 모른다).
 */
interface SpotQueryUseCase {
    /**
     * 위치 기반 관광지 오프셋 페이지. 거리 오름차순 정렬, 비관광 필터·거리계산 포함.
     * [memberId]가 있으면(로그인) 이미 반응(좋아요/패스)한 관광지를 제외한다 — 중복 노출 방지. 비로그인은 제외 없음.
     */
    fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): SpotPageView

    /** 현재 언어에서 유효한 카테고리 목록(AC-8). */
    fun availableCategories(locale: DomainLocale): List<SpotCategory>

    /**
     * contentId 단건 상세 **본문**(SPEC-0003). 캐시를 앞세우며 upstream은 detailCommon2 1콜.
     * 갤러리·운영정보·이용안내·좋아요 수는 GraphQL 필드가 요청될 때 아래 메서드로 따로 조회한다(지연 조회).
     *
     * 해소 실패는 SpotDetailNotFoundException, 한도 초과는 UpstreamRateLimitedException, 그 외 장애는
     * UpstreamUnavailableException(모두 SpotQueryException → 최상위 errors[]).
     */
    fun spotDetail(contentId: String, locale: DomainLocale): SpotDetailView

    /** 갤러리(요청 시에만). 캐시 → 미스면 detailImage2 1콜. */
    fun spotGallery(contentId: String, locale: DomainLocale): List<SpotImageView>

    /** 운영 정보(요청 시에만). 캐시 → 미스면 detailIntro2 1콜. 표시할 값이 없으면 null. */
    fun spotIntro(contentId: String, category: SpotCategory, locale: DomainLocale): SpotIntroView?

    /** 이용안내 항목(요청 시에만). 캐시 → 미스면 detailInfo2 1콜. */
    fun spotInfoItems(contentId: String, category: SpotCategory, locale: DomainLocale): List<SpotInfoItemView>

    /** 좋아요 수(요청 시에만). 캐시 비대상, 매번 실시간 집계(AC-14). */
    fun spotLikeCount(contentId: String, locale: DomainLocale): Long
}
