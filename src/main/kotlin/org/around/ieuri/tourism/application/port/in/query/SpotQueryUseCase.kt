package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.application.query.LikedSpotsQuery
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
     * [memberId]가 있으면(로그인) 이미 반응(좋아요/패스)한 관광지를 제외한다. 비로그인은 제외 없음.
     */
    fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): SpotPageView

    /** 현재 언어에서 유효한 카테고리 목록. */
    fun availableCategories(locale: DomainLocale): List<SpotCategory>

    /**
     * `spot.id` 단건 상세 본문. 캐시를 앞세우며 upstream은 detailCommon2 1콜. 갤러리·운영정보·이용안내·
     * 좋아요 수는 GraphQL 필드가 요청될 때 아래 메서드로 따로 조회한다.
     *
     * **본문이 upstream에 없어도 성공한다** — `spot` 행이 보유한 값으로 응답을 구성하고
     * overview·homepage·tel만 null이 된다. `SPOT_NOT_FOUND`는 id 자체가 해석되지 않을 때만 발생한다.
     *
     * **[locale]은 식별이 아니라 검증에 쓴다** — [spotId]가 이미 언어를 품고 있으며, 요청 언어와 다른
     * 언어의 id가 오면 SpotDetailNotFoundException으로 거부한다.
     */
    fun spotDetail(spotId: Long, locale: DomainLocale): SpotDetailView

    /** 갤러리(요청 시에만). 캐시 → 미스면 detailImage2 1콜. */
    fun spotGallery(spotId: Long, locale: DomainLocale): List<SpotImageView>

    /** 운영 정보(요청 시에만). 캐시 → 미스면 detailIntro2 1콜. 표시할 값이 없으면 null. */
    fun spotIntro(spotId: Long, category: SpotCategory, locale: DomainLocale): SpotIntroView?

    /** 이용안내 항목(요청 시에만). 캐시 → 미스면 detailInfo2 1콜. */
    fun spotInfoItems(spotId: Long, category: SpotCategory, locale: DomainLocale): List<SpotInfoItemView>

    /** 좋아요 수(요청 시에만). 캐시 비대상이며 매번 실시간 집계한다. */
    fun spotLikeCount(spotId: Long): Long

    /**
     * 회원이 좋아요한 spot 한 페이지. likedAt 내림차순, 동률은 spot.id로 타이브레이크.
     *
     * 요청 언어로 한정하며, [memberId] 본인 것만 조회한다 — 미인증 거부는 리졸버가 판정하고 이 포트는
     * 이미 인증된 회원을 전제한다. [LikedSpotsQuery.category]가 현재 언어에서 유효하지 않으면
     * CATEGORY_NOT_AVAILABLE_FOR_LOCALE로 거부한다.
     */
    fun likedSpots(query: LikedSpotsQuery, locale: DomainLocale, memberId: Long): CursorPage<LikedSpotView>
}
