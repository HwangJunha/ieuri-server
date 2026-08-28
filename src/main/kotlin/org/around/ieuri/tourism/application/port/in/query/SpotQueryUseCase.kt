package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.context.AppKey
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

/**
 * 관광지 조회 인바운드 포트(CQRS 읽기). 언어는 요청 스코프에서 결정돼 명시 파라미터로 전달된다
 * (리졸버가 Accept-Language 헤더에서 읽어 넘긴다 — application은 서블릿/헤더를 모른다).
 */
interface SpotQueryUseCase {
    /**
     * 위치 기반 관광지 오프셋 페이지. 거리 오름차순 정렬, 비관광 필터·거리계산 포함.
     * [memberId]가 있으면(로그인) 이미 반응(좋아요/패스)한 관광지를 제외한다. 비로그인은 제외 없음.
     */
    fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): SpotPageView

    /**
     * 이 앱·이 언어에 노출되는 카테고리를 구성된 순서대로(SPEC-0020).
     *
     * **`app_category` 한 테이블이 유일한 출처다** — 이전에는 언어 비대칭이 코드에, 앱별
     * 구성이 `community_chip`에 따로 있어 같은 축이 두 벌로 갈라져 있었다. community의
     * 상단 칩도 이 메서드를 지난다.
     *
     * 구성이 없으면 빈 목록이며 오류가 아니다 — 알 수 없는 앱 키가 그대로 도착할 수 있다.
     */
    fun availableCategories(appKey: AppKey, locale: DomainLocale): List<AppCategoryView>

    /**
     * 위와 같은 구성을, 도메인 타입 없이 **문자열 축으로** 돌려준다. 다른 도메인이 쓰는 진입점이다.
     *
     * 언어 코드와 카테고리 코드를 문자열로 주고받는다 — 호출하는 도메인이 tourism의
     * `DomainLocale`·`SpotCategory`를 import하면 그 도메인만 따로 떼어낼 수 없게 된다.
     * 모르는 언어 코드는 폴백 언어로 읽는다.
     */
    fun availableCategoryCodes(appKey: AppKey, languageCode: String): List<AppCategoryCode>

    /**
     * 카테고리 코드 → 표시 정보를 **한 번에**(SPEC-0020). 목록 응답이 카드마다 묻지 않게 한다.
     *
     * 정의가 없는 코드도 키가 있다 — 코드 자체를 라벨로 쓰는 폴백이 담긴다. 알 수 없는
     * 카테고리라고 카드를 감추지 않기 위해서다.
     */
    fun categoryDisplays(codes: Set<String>, locale: DomainLocale): Map<String, CategoryDisplay>

    /**
     * `spot.id` 단건 상세 본문. 캐시를 앞세우며 upstream은 detailCommon2 1콜. 갤러리·운영정보·이용안내·
     * 좋아요 수는 GraphQL 필드가 요청될 때 아래 메서드로 따로 조회한다.
     *
     * **본문이 upstream에 없어도 성공한다** — `spot` 행이 보유한 값으로 응답을 구성하고
     * overview·homepage·tel만 null이 된다. `SPOT_NOT_FOUND`는 id 자체가 해석되지 않을 때만 발생한다.
     *
     * **[locale]은 식별이 아니라 검증에 쓴다** — [catalogId]가 이미 언어를 품고 있으며, 요청 언어와 다른
     * 언어의 id가 오면 SpotDetailNotFoundException으로 거부한다.
     */
    fun spotDetail(catalogId: Long, locale: DomainLocale): SpotDetailView

    /** 갤러리(요청 시에만). 캐시 → 미스면 detailImage2 1콜. */
    fun spotGallery(catalogId: Long, locale: DomainLocale): List<SpotImageView>

    /** 운영 정보(요청 시에만). 캐시 → 미스면 detailIntro2 1콜. 표시할 값이 없으면 null. */
    fun spotIntro(catalogId: Long, category: SpotCategory, locale: DomainLocale): SpotIntroView?

    /** 이용안내 항목(요청 시에만). 캐시 → 미스면 detailInfo2 1콜. */
    fun spotInfoItems(catalogId: Long, category: SpotCategory, locale: DomainLocale): List<SpotInfoItemView>

    /**
     * 좋아요 수(요청 시에만). 캐시 비대상이며 매번 실시간 집계한다.
     * **[locale] 안에서만 센다**(SPEC-0029 AC-13.5) — 다른 언어의 좋아요는 합산되지 않는다.
     */
    fun spotLikeCount(catalogId: Long, locale: DomainLocale): Long

    /**
     * 이 회원이 **[locale]에서** 이 장소에 남긴 최신 반응 코드(`LIKE`·`DISLIKE`).
     * 반응한 적 없으면 null이며, 다른 언어에서 누른 것은 여기 보이지 않는다(AC-13.2).
     *
     * **문자열 축으로 돌려주는 이유는 [availableCategoryCodes]와 같다** — 호출하는 도메인이
     * tourism의 `ReactionType`을 import하면 그 도메인만 따로 떼어낼 수 없게 된다.
     * community의 질문/답변 작성 자격 판정(SPEC-0026 R5)이 이 진입점을 지난다.
     *
     * `reactToSpot`에는 취소 수단이 없어 null(반응 없음)과 DISLIKE는 서로 다른 상태이며,
     * 작성 자격 관점에서는 둘 다 자격 없음이다.
     */
    fun spotViewerReaction(catalogId: Long, memberId: Long, locale: DomainLocale): String?

    /**
     * 카탈로그가 보유한 최소 식별 정보. **upstream을 호출하지 않는다** — 한 행을 읽을 뿐이다.
     *
     * 다른 도메인이 장소 id의 유효성을 확인하고 칩·요약을 그릴 때 쓰는 진입점이다. 그 용도로
     * [spotDetail]을 쓰면 검증 한 번에 TourAPI 왕복이 붙는다.
     *
     * **[languageCode]에서 해소되지 않으면 null이다**(SPEC-0029 R-24). 부재는 예외가 아니며,
     * 이 null이 곧 "활성 언어에 없는 장소"라는 뜻이다 — 존재 검사가 이 값으로 판정한다(R-25).
     *
     * 언어를 문자열로 받는 이유는 [availableCategoryCodes]와 같다 — 호출하는 도메인이
     * tourism의 `DomainLocale`을 import하면 그 도메인만 따로 떼어낼 수 없게 된다.
     * 모르는 언어 코드는 폴백 언어로 읽는다.
     */
    fun spotSummary(catalogId: Long, languageCode: String): SpotSummaryView?

    /**
     * 여러 카탈로그 요약을 **한 번에**. 목록을 그리는 호출자가 행마다 묻지 않게 한다.
     * [languageCode]에서 해소되지 않는 id는 결과에 키가 없다.
     */
    fun spotSummaries(catalogIds: List<Long>, languageCode: String): Map<Long, SpotSummaryView>

    /**
     * 회원이 좋아요한 spot 한 페이지. likedAt 내림차순, 동률은 spot.id로 타이브레이크.
     *
     * 요청 언어로 한정하며, [memberId] 본인 것만 조회한다 — 미인증 거부는 리졸버가 판정하고 이 포트는
     * 이미 인증된 회원을 전제한다.
     */
    fun likedSpots(query: LikedSpotsQuery, locale: DomainLocale, memberId: Long): CursorPage<LikedSpotView>

    /**
     * 좋아요한 spot 중 검색어에 일치하는 한 페이지(SPEC-0033). 정렬·커서 규약은 [likedSpots]와
     * 같고 **검색어가 순서에 영향을 주지 않는다**(R3).
     *
     * **조회처가 [likedSpots]와 다르다** — 검색 노드(논리 복제본)를 보므로 방금 누른 좋아요가
     * 아직 없을 수 있다. 유스케이스를 나눈 이유가 이 차이이며, 두 메서드를 합치면 인자 하나로
     * 신선도가 달라지는 것이 호출자에게 보이지 않는다.
     *
     * 검증 실패는 EMPTY_QUERY·QUERY_TOO_LONG.
     */
    fun searchLikedSpots(query: LikedSpotsQuery, text: String, locale: DomainLocale, memberId: Long): CursorPage<LikedSpotView>
}
