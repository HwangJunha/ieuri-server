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
     * [memberId]가 있으면(로그인) 이미 반응(좋아요/패스)한 관광지를 제외한다 — 중복 노출 방지. 비로그인은 제외 없음.
     */
    fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): SpotPageView

    /** 현재 언어에서 유효한 카테고리 목록(AC-8). */
    fun availableCategories(locale: DomainLocale): List<SpotCategory>

    /**
     * `spot.id` 단건 상세 **본문**(SPEC-0003). 캐시를 앞세우며 upstream은 detailCommon2 1콜.
     * 갤러리·운영정보·이용안내·좋아요 수는 GraphQL 필드가 요청될 때 아래 메서드로 따로 조회한다(지연 조회).
     *
     * **본문이 upstream에 없어도 성공한다**(SPEC-0008 R-1·R-2). 그 경우 `spot` 행이 보유한 값으로
     * 응답을 구성하고(title·category·thumbnailImageUrl·copyright는 non-null 유지),
     * overview·homepage·tel은 null이 된다. 부재는 오류가 아니라 성공 경로다 — `SPOT_NOT_FOUND`는
     * id 자체가 해석되지 않을 때만 발생한다(AC-2·AC-3).
     *
     * **[locale]은 식별이 아니라 검증에 쓴다.** [spotId]가 이미 언어를 품고 있으므로 조회 키로는
     * 필요 없지만, 요청 언어와 다른 언어의 id가 오면 거부해야 한다 — 클라이언트가 언어를 바꾸면
     * 목록을 다시 받아야 하며, 이전 언어의 id로 상세를 요청하는 것은 버그다. 조용히 다른 언어
     * 콘텐츠를 돌려주면 그 버그가 드러나지 않는다.
     *
     * 해소 실패(존재하지 않는 id·언어 불일치)는 SpotDetailNotFoundException, 한도 초과는
     * UpstreamRateLimitedException, 그 외 장애는 UpstreamUnavailableException
     * (모두 SpotQueryException → 최상위 errors[]).
     */
    fun spotDetail(spotId: Long, locale: DomainLocale): SpotDetailView

    /** 갤러리(요청 시에만). 캐시 → 미스면 detailImage2 1콜. */
    fun spotGallery(spotId: Long, locale: DomainLocale): List<SpotImageView>

    /** 운영 정보(요청 시에만). 캐시 → 미스면 detailIntro2 1콜. 표시할 값이 없으면 null. */
    fun spotIntro(spotId: Long, category: SpotCategory, locale: DomainLocale): SpotIntroView?

    /** 이용안내 항목(요청 시에만). 캐시 → 미스면 detailInfo2 1콜. */
    fun spotInfoItems(spotId: Long, category: SpotCategory, locale: DomainLocale): List<SpotInfoItemView>

    /**
     * 좋아요 수(요청 시에만). 캐시 비대상, 매번 실시간 집계(AC-14).
     * upstream을 타지 않으므로 언어 검증이 필요 없다 — [spotId]만으로 집계가 확정된다.
     */
    fun spotLikeCount(spotId: Long): Long

    /**
     * 회원이 좋아요한 spot 목록(SPEC-0008 R-15~R-17). likedAt 내림차순, 동률은 spot.id로 타이브레이크.
     *
     * **요청 언어로 한정한다**(AC-49) — 목록이 언어를 섞으면 다른 언어의 id가 카드로 나오고,
     * 그 카드를 탭하는 순간 [spotDetail]의 교차 언어 거부에 걸린다. 목록에는 멀쩡히 보이는데
     * 진입만 실패하는 형태라 사용자에게 원인이 드러나지 않는다.
     *
     * [memberId]는 인증 컨텍스트에서 온 값이며 본인 것만 조회한다(AC-48). 미인증 거부는 리졸버가
     * 판정한다 — 이 포트는 이미 인증된 회원을 전제한다.
     *
     * [LikedSpotsQuery.category]가 있으면 그 카테고리만 반환한다. `spots`와 동일하게 현재 언어에서
     * 유효하지 않은 카테고리는 CATEGORY_NOT_AVAILABLE_FOR_LOCALE로 거부한다 — 두 목록이 같은
     * 카테고리 축을 쓰므로 판정도 같아야 한다.
     */
    fun likedSpots(query: LikedSpotsQuery, locale: DomainLocale, memberId: Long): CursorPage<LikedSpotView>
}
