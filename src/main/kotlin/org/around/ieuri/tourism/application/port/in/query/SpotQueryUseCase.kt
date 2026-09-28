package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.AppCategoryCode
import org.around.ieuri.tourism.application.query.AppCategoryView
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.application.query.LikedSpotsQuery
import org.around.ieuri.tourism.application.query.SpotDetailView
import org.around.ieuri.tourism.application.query.SpotImageView
import org.around.ieuri.tourism.application.query.SpotInfoItemView
import org.around.ieuri.tourism.application.query.SpotIntroView
import org.around.ieuri.tourism.application.query.SpotPageView
import org.around.ieuri.tourism.application.query.SpotSummaryView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/** 관광지 조회 인바운드 포트(CQRS 읽기). 언어는 명시 파라미터로 받는다. */
interface SpotQueryUseCase {
    /**
     * 위치 기반 관광지 오프셋 페이지. 거리 오름차순.
     * - [memberId]가 있으면 이미 반응한 관광지를 제외한다. null이면 제외 없음.
     */
    fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): SpotPageView

    /**
     * 이 앱·이 언어에 노출되는 카테고리를 구성된 순서대로(SPEC-0020). 출처는 `app_category`.
     * - 구성이 없으면 오류가 아니라 빈 목록이다.
     */
    fun availableCategories(appKey: AppKey, locale: DomainLocale): List<AppCategoryView>

    /**
     * 위와 같은 구성을 문자열 축으로 돌려준다. 다른 도메인이 쓰는 진입점이다.
     * - 모르는 언어 코드는 폴백 언어로 읽는다.
     */
    fun availableCategoryCodes(appKey: AppKey, languageCode: String): List<AppCategoryCode>

    /**
     * 카테고리 코드 → 표시 정보를 한 번에(SPEC-0020).
     * - 정의가 없는 코드도 키가 있고, 코드 자체를 라벨로 쓰는 폴백이 담긴다.
     */
    fun categoryDisplays(codes: Set<String>, locale: DomainLocale): Map<String, CategoryDisplay>

    /**
     * 카탈로그 단건 상세 본문. 갤러리·운영정보·이용안내·좋아요 수는 아래 메서드로 따로 조회한다.
     * - 본문이 upstream에 없어도 성공한다 — overview·homepage·tel만 null이 된다.
     * - `SPOT_NOT_FOUND`는 id 자체가 해석되지 않을 때만 발생한다.
     * - [locale]에 원본이 없으면 거부한다.
     */
    fun spotDetail(catalogId: Long, locale: DomainLocale): SpotDetailView

    /** 갤러리(요청 시에만). 캐시 → 미스면 detailImage2 1콜. */
    fun spotGallery(catalogId: Long, locale: DomainLocale): List<SpotImageView>

    /** 운영 정보(요청 시에만). 캐시 → 미스면 detailIntro2 1콜. 표시할 값이 없으면 null. */
    fun spotIntro(catalogId: Long, category: SpotCategory, locale: DomainLocale): SpotIntroView?

    /** 이용안내 항목(요청 시에만). 캐시 → 미스면 detailInfo2 1콜. */
    fun spotInfoItems(catalogId: Long, category: SpotCategory, locale: DomainLocale): List<SpotInfoItemView>

    /**
     * 좋아요 수(요청 시에만). 캐시하지 않고 매번 집계한다.
     * - [locale] 안에서만 센다(SPEC-0029 AC-13.5).
     */
    fun spotLikeCount(catalogId: Long, locale: DomainLocale): Long

    /**
     * 이 회원이 [locale]에서 이 장소에 남긴 최신 반응 코드(`LIKE`·`DISLIKE`). 다른 도메인이 쓰는 문자열 축.
     * - 반응한 적 없으면 null이며, 다른 언어에서 누른 것은 보이지 않는다(AC-13.2).
     */
    fun spotViewerReaction(catalogId: Long, memberId: Long, locale: DomainLocale): String?

    /**
     * 카탈로그가 보유한 최소 식별 정보. upstream을 호출하지 않는다. 다른 도메인이 쓰는 진입점이다.
     * - [languageCode]에서 해소되지 않으면 예외가 아니라 null이다(SPEC-0029 R-24).
     * - 모르는 언어 코드는 폴백 언어로 읽는다.
     */
    fun spotSummary(catalogId: Long, languageCode: String): SpotSummaryView?

    /**
     * 여러 카탈로그 요약을 한 번에.
     * - [languageCode]에서 해소되지 않는 id는 결과에 키가 없다.
     */
    fun spotSummaries(catalogIds: List<Long>, languageCode: String): Map<Long, SpotSummaryView>

    /**
     * 회원이 좋아요한 spot 한 페이지. likedAt 내림차순, 동률은 spot.id.
     * - 요청 언어로 한정하며 인증된 [memberId] 본인 것만 조회한다.
     */
    fun likedSpots(query: LikedSpotsQuery, locale: DomainLocale, memberId: Long): CursorPage<LikedSpotView>

    /**
     * 좋아요한 spot 중 검색어에 일치하는 한 페이지(SPEC-0033). 정렬·커서는 [likedSpots]와 같다.
     * - 검색 노드(복제본)를 보므로 방금 누른 좋아요가 아직 없을 수 있다.
     * - 검증 실패는 EMPTY_QUERY·QUERY_TOO_LONG.
     */
    fun searchLikedSpots(query: LikedSpotsQuery, text: String, locale: DomainLocale, memberId: Long): CursorPage<LikedSpotView>
}
