package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광지 관련 읽기 아웃바운드 포트(CQRS 읽기). 애그리거트를 우회해 필요한 값만 프로젝션한다.
 * spot 조회에 필요한 DB 읽기를 한 포트로 모은다(반경 조회, 반응 기록 기반 집계·필터).
 */
interface SpotQueryPort {
    /**
     * 반경 안의 관광지를 **거리 오름차순**으로 한 페이지 조회한다. TourAPI를 호출하지 않고 적재된
     * spot 테이블만 본다. [SpotSearchResult.distanceMeters]는 DB가 계산한 구면 거리다.
     */
    fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult>

    /** [query] 조건에 맞는 전체 건수(페이지 무관). SpotPage.totalCount 산출용. */
    fun countNearby(query: NearbySpotSearch): Int

    /**
     * 이 회원이 이미 반응한 **카탈로그 id** 전체. 반경 조회의 제외 집합이다.
     * **언어를 받지 않는다**(SPEC-0019) — 좋아요는 언어와 무관한 장소 단위 행위다.
     */
    fun findReactedSpotIds(memberId: Long): Set<Long>

    /**
     * 이 **카탈로그**에 대한 LIKE 반응 수. DISLIKE는 세지 않으며, 반응이 없으면 0.
     * 같은 장소의 언어별 좋아요가 합산된다(SPEC-0019 R-10).
     */
    fun countLikes(spotId: Long): Long

    /**
     * 이 회원의 이 카탈로그에 대한 **최신 반응**. 반응한 적 없으면 null.
     *
     * `reactToSpot`은 덮어쓰기만 가능하고 취소 수단이 없어 행이 하나뿐이다 — 그 행이 곧
     * 최신 반응이다. 쓰기 포트(SpotReactionPort.find)와 같은 행을 보지만 이쪽은 읽기
     * 경로라 애그리게이트를 복원하지 않는다.
     */
    fun findReaction(catalogId: Long, memberId: Long): ReactionType?

    /**
     * 카탈로그 id → upstream 식별 정보. [language] 원본을 우선하되 **없으면 다른 언어로
     * 떨어진다.** 카탈로그에 원본이 하나도 없을 때만 null이다.
     *
     * **`spot`을 읽는 유일한 조회 경로다**(SPEC-0019). TourAPI가 contentId를 언어별로 발급하므로
     * 상세를 물으려면 원본 행이 필요하다.
     *
     * **폴백이 필요한 이유가 이 스펙의 목적 그 자체다.** 번역으로 격차를 메운 카탈로그는
     * 그 언어의 원본이 없다(실측 36,000건 이상) — 목록·검색에는 나오는데 상세만 막히면
     * 번역이 무의미해진다. 돌려받은 [SpotIdentity.language]가 실제 원본 언어이며, 호출자는
     * **그 언어로 upstream에 물어야 한다** — 없는 contentId로 물으면 실패한다.
     */
    fun findIdentity(catalogId: Long, language: DomainLocale): SpotIdentity?

    /**
     * 카탈로그가 [language]로 보유한 상세 표시 값. 상세 본문이 부재할 때 응답을 구성하고 부재
     * 행의 NOT NULL 컬럼을 채우는 재료다.
     *
     * [language] 번역을 우선하되 **없으면 다른 언어로 떨어진다.** 외국어에만 있는 장소는 KO
     * 행이 없어(실측 4,196건) null을 돌려주면 그 장소에 글을 못 쓰고 기존 글의 칩도 사라진다.
     * 번역이 하나도 없는 카탈로그만 null이며, 그것은 원본이 사라진 장소다.
     */
    fun findSpotBasics(catalogId: Long, language: DomainLocale): SpotBasics?

    /**
     * 여러 카탈로그의 기본 정보를 **IN 절 한 번**으로. [language]를 우선하되 없으면 다른
     * 언어로 떨어진다. 없는 id와 번역이 하나도 없는 카탈로그만 결과에 키가 없다.
     */
    fun findSpotBasicsIn(catalogIds: List<Long>, language: DomainLocale): Map<Long, SpotBasics>

    /** 회원이 좋아요(LIKE)한 spot 한 페이지. DISLIKE는 제외하고 요청 언어로 한정한다. */
    fun findLikedSpots(query: LikedSpotsSearch): CursorPage<LikedSpotView>

    /**
     * 반경 안의 **카탈로그 id**(SPEC-0019 R-12). [limit]을 넘으면 잘린다 — 호출자가 그것을
     * 감안해야 한다(가까운 순이라 잘리는 것은 먼 쪽이다).
     *
     * 다른 도메인이 장소를 저장하는 축이 카탈로그이므로(`community_post_spot.catalog_id`)
     * 여기서도 같은 축을 돌려준다. 좌표가 없는 카탈로그는 애초에 반경 조건에 걸리지 않는다.
     */
    fun findSpotIdsWithin(
        longitude: Double,
        latitude: Double,
        radiusMeters: Int,
        limit: Int,
        categoryCode: String? = null,
    ): List<Long>
}

/**
 * `spot` 행이 보유한 상세 표시 값(read DTO). 본문 부재 시 응답·적재의 재료가 된다.
 * attributionText는 copyright에서 도출되므로 담지 않는다.
 */
data class SpotBasics(
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val category: SpotCategory,
    val copyright: CopyrightType,
    /** 경도. 다른 도메인이 이 좌표를 복사해 자기 반경 조회에 쓴다. */
    val longitude: Double,
    /** 위도. */
    val latitude: Double,
)

/**
 * 좋아요 목록 조회 입력. [language]로 한정하고 [memberId] 본인 것만 조회한다.
 * [after]는 불투명 커서이며 정렬 키(likedAt, spotId)에서 파생된다.
 */
data class LikedSpotsSearch(
    val memberId: Long,
    val language: DomainLocale,
    /** 카테고리 선택. 비어 있으면 전체. 값이 있으면 `spot_catalog.category`를 IN으로 거른다. */
    val categories: CategorySelection,
    val first: Int,
    val after: String?,
)

/**
 * 반경 조회 입력. [excludedSpotIds]는 로그인 회원이 이미 반응한 항목으로, **DB 쿼리 단계에서**
 * 제외해야 한다 — 조회 후 애플리케이션에서 걸러내면 페이지가 요청한 size보다 작아진다.
 */
data class NearbySpotSearch(
    /**
     * 원문 보유 필터. **null이면 전체**, true면 그 언어 원문이 있는 것만, false면 번역본만.
     *
     * 클라이언트가 원문을 먼저 소진한 뒤 false로 넘어오는 흐름을 위한 값이다 — 서버가
     * 정렬로 끼워넣지 않는다. 정렬로 하면 거리·인기순을 요청했는데 다른 축이 앞서는
     * 숨은 규칙이 되고, 커서에도 그 축을 실어야 해서 형식이 깨진다.
     */
    val hasOrigin: Boolean?,
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체. 값이 여럿이면 IN으로 거른다(OR). */
    val categories: CategorySelection,
    val excludedSpotIds: Set<Long>,
    val limit: Int,
    val offset: Int,
)

/**
 * 반경 조회 결과 1건(read DTO). 리졸버가 GraphQL Spot 타입으로 매핑한다(application은 DGS 무의존).
 * [distanceMeters]는 DB가 ST_Distance로 계산한 요청 좌표로부터의 구면 거리(미터)다.
 * attributionText는 copyright에서 파생되므로 담지 않는다 — 서비스가 도출한다.
 */
data class SpotSearchResult(
    val spotId: Long,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val mapX: Double,
    val mapY: Double,
    val category: SpotCategory,
    val copyright: CopyrightType,
    val distanceMeters: Double,
)

/** `spot.id`가 가리키는 행의 upstream 식별 정보. 상세 조회의 TourAPI 폴백과 언어 검증에만 쓴다. */
data class SpotIdentity(
    /**
     * 원본 `spot.id`. **상세 캐시·`spot_detail` 계열의 키다.**
     *
     * 카탈로그 id를 그 자리에 쓰면 안 된다 — `spot_detail*`의 FK가 `spot(id)`를 가리키므로
     * 적재가 FK 위반으로 조용히 실패하고(리스너가 삼킨다), 우연히 값이 겹치면 **다른 장소의
     * 본문이 적재된다.** 두 id 공간이 겹칠 수 있다는 것이 이 필드가 존재하는 이유다.
     */
    val spotId: Long,
    val contentId: String,
    val language: DomainLocale,
)
